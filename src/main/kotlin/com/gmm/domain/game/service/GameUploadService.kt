package com.gmm.domain.game.service

import com.gmm.domain.build.service.BuildLimits
import com.gmm.domain.build.service.BuildProcessor
import com.gmm.domain.build.storage.BuildStorage
import com.gmm.domain.game.entity.GameBuild
import com.gmm.domain.game.presentation.GameRequest
import com.gmm.domain.game.presentation.GameResponse
import com.gmm.domain.image.storage.ImageStorage
import com.gmm.global.auth.AuthUser
import com.gmm.global.config.GmmProperties
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import com.gmm.global.support.Ids
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/**
 * Orchestrates game writes that carry a build. It is not transactional: build processing takes seconds
 * and must not hold a database transaction. The order is fixed so a failure never leaves a game row
 * that points to a missing folder:
 *
 * 1. Check permissions and the game limit (fails fast before reading the zip).
 * 2. Validate and extract the zip in a private workspace.
 * 3. Move the build to its public folder.
 * 4. Commit the database row. On failure, delete the new folder.
 * 5. After commit, delete the replaced folder (see [GameService.update]).
 */
@Service
class GameUploadService(
    private val gameService: GameService,
    private val buildStorage: BuildStorage,
    private val jsonMapper: JsonMapper,
    private val imageStorage: ImageStorage,
    properties: GmmProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val processor =
        BuildProcessor(
            BuildLimits(
                maxExtractedBytes = properties.build.maxExtractedSize.toBytes(),
                maxFileCount = properties.build.maxFileCount,
            ),
        )

    fun create(user: AuthUser, data: String, file: MultipartFile): GameResponse {
        val request = parse(data)
        val jamId = request.requireJamId()
        val details = request.toDetails(imageStorage::isStoredUrl)
        gameService.checkCanCreate(user, jamId)

        val gameId = Ids.new()
        val build = storeBuild(gameId, file)
        val game =
            try {
                gameService.create(gameId, user, jamId, details, build)
            } catch (e: Exception) {
                buildStorage.delete(build.path)
                throw e
            }
        log.info("Create game {} with build {}", game.id, build.path)
        return GameResponse.from(game, buildStorage::publicUrl)
    }

    /** `jamId` in [data] is ignored: a game cannot move to another jam. */
    fun update(user: AuthUser, gameId: String, data: String, file: MultipartFile?): GameResponse {
        val details = parse(data).toDetails(imageStorage::isStoredUrl)
        gameService.checkCanUpdate(user, gameId)

        val build = file?.let { storeBuild(gameId, it) }
        val game =
            try {
                gameService.update(user, gameId, details, build)
            } catch (e: Exception) {
                build?.let { buildStorage.delete(it.path) }
                throw e
            }
        if (build != null) log.info("Replace build of game {} with {}", game.id, build.path)
        return GameResponse.from(game, buildStorage::publicUrl)
    }

    private fun parse(data: String): GameRequest = try {
        jsonMapper.readValue(data, GameRequest::class.java)
    } catch (_: JacksonException) {
        throw GmmException(ErrorCode.INVALID_GAME_INPUT, "게임 정보 형식이 올바르지 않아요.")
    }

    private fun storeBuild(gameId: String, file: MultipartFile): GameBuild {
        if (file.isEmpty) throw GmmException(ErrorCode.BUILD_REQUIRED)
        val fileName = displayFileName(file.originalFilename)

        val uploadId = Ids.new()
        val workspace = buildStorage.createWorkspace(uploadId)
        try {
            val zip = workspace.resolve("upload.zip")
            file.transferTo(zip)
            val extracted = workspace.resolve("build")
            val buildName = processor.process(zip, extracted)
            val path = buildStorage.publish(extracted, gameId, uploadId)
            return GameBuild(path = path, name = buildName, fileName = fileName)
        } finally {
            buildStorage.deleteWorkspace(workspace)
        }
    }

    companion object {
        /** In characters (code points), as the `VARCHAR(255)` column counts them. */
        private const val MAX_FILE_NAME_LENGTH = 255
        private const val ZIP = ".zip"

        /**
         * The name stored for display. Keeps only the base name, because browsers may send a full client path.
         * Checks the extension on the full name, then shortens a long name before the extension so it stays `.zip`.
         */
        internal fun displayFileName(original: String?): String {
            val name = original
                ?.replace('\\', '/')
                ?.substringAfterLast('/')
                ?.filterNot { it.isISOControl() }
                ?.ifBlank { null }
                ?: return "build.zip"
            if (!name.endsWith(ZIP, ignoreCase = true)) {
                throw GmmException(ErrorCode.BUILD_INVALID, ".zip 파일만 올릴 수 있어요.")
            }
            if (name.codePointCount(0, name.length) <= MAX_FILE_NAME_LENGTH) return name
            val stem = name.dropLast(ZIP.length)
            // Cut at a code point boundary so a surrogate pair (emoji, rare Hanja) is never split.
            return stem.substring(0, stem.offsetByCodePoints(0, MAX_FILE_NAME_LENGTH - ZIP.length)) +
                name.takeLast(ZIP.length)
        }
    }
}
