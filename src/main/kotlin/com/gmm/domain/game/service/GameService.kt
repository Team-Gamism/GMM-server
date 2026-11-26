package com.gmm.domain.game.service

import com.gmm.domain.build.storage.BuildStorage
import com.gmm.domain.game.entity.Game
import com.gmm.domain.game.entity.GameBuild
import com.gmm.domain.game.entity.GameDetails
import com.gmm.domain.game.repository.GameRepository
import com.gmm.domain.jam.repository.JamRepository
import com.gmm.global.auth.AuthUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import com.gmm.global.support.afterCommit
import com.gmm.global.support.nowMillis
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class GameService(
    private val gameRepository: GameRepository,
    private val jamRepository: JamRepository,
    private val buildStorage: BuildStorage,
    private val clock: Clock,
) {
    /** Participants upload only while the jam is ongoing. Admins upload at any time. */
    @Transactional(readOnly = true)
    fun checkCanUpload(user: AuthUser, jamId: String) {
        // 400, not 404: the frontend turns 404 into the not-found page, which loses the upload form.
        val jam =
            jamRepository.findById(jamId).orElseThrow {
                GmmException(ErrorCode.INVALID_GAME_INPUT, "게임잼을 선택해 주세요.")
            }
        if (!user.isAdmin && !jam.statusAt(clock.instant()).acceptsUploads) {
            throw GmmException(ErrorCode.JAM_NOT_ONGOING)
        }
    }

    /** [checkCanUpload] plus the limit of one game per participant and jam. Admins have no limit. */
    @Transactional(readOnly = true)
    fun checkCanCreate(user: AuthUser, jamId: String) {
        checkCanUpload(user, jamId)
        if (!user.isAdmin && gameRepository.countByJamIdAndOwnerId(jamId, user.id) >= MAX_GAMES_PER_JAM) {
            throw GmmException(ErrorCode.GAME_LIMIT_REACHED)
        }
    }

    @Transactional
    fun create(gameId: String, user: AuthUser, jamId: String, details: GameDetails, build: GameBuild): Game {
        // Lock the jam row so two uploads sent at once cannot both pass the limit.
        jamRepository.findForUpdate(jamId)
        // Check again: the jam can end or be deleted, or another upload can commit, while the build is processed.
        checkCanCreate(user, jamId)
        return gameRepository.save(
            Game(
                id = gameId,
                jamId = jamId,
                ownerId = user.id,
                details = details,
                build = build,
                createdAt = clock.nowMillis(),
            ),
        )
    }

    /**
     * Owners and admins edit the details and the build only while the jam is ongoing, admins included,
     * so the game that is rated cannot change afterwards. Deleting stays possible.
     */
    @Transactional(readOnly = true)
    fun checkCanUpdate(user: AuthUser, gameId: String) {
        findManageable(user, gameId)
    }

    /**
     * Updates the game. When [build] is given, the previous build folder is deleted after commit.
     * If another write to the game commits first, this one fails with 409 and deletes nothing (see [Game.version]).
     */
    @Transactional
    fun update(user: AuthUser, gameId: String, details: GameDetails, build: GameBuild?): Game {
        // Checks again: the jam can end while the build is processed.
        val game = findManageable(user, gameId)
        val now = clock.nowMillis()
        game.updateDetails(details, now)
        if (build != null) {
            game.replaceBuild(build, now)?.let { previous -> afterCommit { buildStorage.delete(previous) } }
        }
        return game
    }

    /**
     * Deletes the game. The database cascades to its reviews and awards; the build folder
     * is deleted after commit because file deletion cannot roll back.
     */
    @Transactional
    fun delete(user: AuthUser, gameId: String) {
        val game = findManageable(user, gameId, forDelete = true)
        gameRepository.delete(game)
        afterCommit { buildStorage.deleteGame(game.id) }
    }

    /** The game if [user] may change it; for edits ([forDelete] false) also only while the jam is ongoing. */
    private fun findManageable(user: AuthUser, gameId: String, forDelete: Boolean = false): Game {
        val game = gameRepository.findById(gameId).orElse(null) ?: throw GmmException(ErrorCode.GAME_NOT_FOUND)
        val status = jamRepository.findById(game.jamId).orElseThrow { GmmException(ErrorCode.GAME_NOT_FOUND) }
            .statusAt(clock.instant())
        if (!game.isVisibleTo(user, status)) throw GmmException(ErrorCode.GAME_NOT_FOUND)
        if (!game.canManage(user)) {
            throw GmmException(if (forDelete) ErrorCode.GAME_DELETE_NOT_OWNER else ErrorCode.GAME_NOT_OWNER)
        }
        if (!forDelete && !status.acceptsUploads) throw GmmException(ErrorCode.GAME_EDIT_CLOSED)
        return game
    }

    companion object {
        const val MAX_GAMES_PER_JAM = 1
    }
}
