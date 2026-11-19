package com.gmm.domain.build.storage

import com.gmm.global.config.GmmProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Layout under `gmm.build.root-dir`:
 * - `builds/{gameId}/{uploadId}/` — public, served by nginx at `{publicBaseUrl}/builds/...`
 * - `staging/{uploadId}/` — private work area on the same file system, so publishing is an atomic move
 */
@Component
class LocalDiskBuildStorage(properties: GmmProperties) : BuildStorage {
    private val log = LoggerFactory.getLogger(javaClass)
    private val root: Path = properties.build.rootDir.toAbsolutePath().normalize()
    private val buildsDir: Path = root.resolve("builds")
    private val stagingDir: Path = root.resolve("staging")
    private val publicBaseUrl = properties.build.publicBaseUrl.trimEnd('/')

    init {
        Files.createDirectories(buildsDir)
        Files.createDirectories(stagingDir)
    }

    override fun createWorkspace(uploadId: String): Path =
        Files.createDirectory(stagingDir.resolve(requireId(uploadId)))

    override fun deleteWorkspace(workspace: Path) {
        val dir = workspace.toAbsolutePath().normalize()
        require(dir.parent == stagingDir) { "Workspace must be a direct child of the staging directory" }
        deleteRecursively(dir)
    }

    override fun publish(extractedDir: Path, gameId: String, uploadId: String): String {
        val buildPath = "${requireId(gameId)}/${requireId(uploadId)}"
        val target = buildsDir.resolve(buildPath)
        Files.createDirectories(target.parent)
        Files.move(extractedDir, target, StandardCopyOption.ATOMIC_MOVE)
        return buildPath
    }

    override fun delete(buildPath: String) {
        val (gameId, uploadId) = parseBuildPath(buildPath)
        deleteRecursively(buildsDir.resolve(gameId).resolve(uploadId))
    }

    override fun deleteGame(gameId: String) = deleteRecursively(buildsDir.resolve(requireId(gameId)))

    override fun publicUrl(buildPath: String): String {
        parseBuildPath(buildPath)
        return "$publicBaseUrl/builds/$buildPath"
    }

    /** Deletes published builds that [isReferenced] rejects and stale staging work, older than [olderThan]. */
    fun deleteOrphans(olderThan: Instant, isReferenced: (String) -> Boolean): Int {
        var deleted = 0
        for (workspace in stagingDir.listDirectoryEntries()) {
            if (isOlder(workspace, olderThan)) {
                deleteRecursively(workspace)
                deleted++
            }
        }
        for (gameDir in buildsDir.listDirectoryEntries().filter { it.isDirectory() }) {
            for (uploadDir in gameDir.listDirectoryEntries().filter { it.isDirectory() }) {
                val buildPath = "${gameDir.name}/${uploadDir.name}"
                if (isOlder(uploadDir, olderThan) && !isReferenced(buildPath)) {
                    deleteRecursively(uploadDir)
                    deleted++
                }
            }
            if (gameDir.listDirectoryEntries().isEmpty() && isOlder(gameDir, olderThan)) {
                Files.deleteIfExists(gameDir)
            }
        }
        return deleted
    }

    private fun isOlder(path: Path, threshold: Instant): Boolean =
        Files.getLastModifiedTime(path).toInstant().isBefore(threshold)

    private fun deleteRecursively(dir: Path) {
        if (!Files.exists(dir)) return
        try {
            Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        } catch (e: IOException) {
            log.warn("Fail to delete build directory {}", dir, e)
        }
    }

    private fun parseBuildPath(buildPath: String): Pair<String, String> {
        val parts = buildPath.split('/')
        require(parts.size == 2) { "Build path must be {gameId}/{uploadId}" }
        return requireId(parts[0]) to requireId(parts[1])
    }

    private fun requireId(id: String): String {
        require(ID.matches(id)) { "Build storage IDs must be UUIDs" }
        return id
    }

    companion object {
        private val ID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
