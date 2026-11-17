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
        val jam = jamRepository.findById(jamId).orElseThrow { GmmException(ErrorCode.JAM_NOT_FOUND) }
        if (!user.isAdmin && !jam.statusAt(clock.instant()).acceptsUploads) {
            throw GmmException(ErrorCode.JAM_NOT_ONGOING)
        }
    }

    @Transactional
    fun create(gameId: String, user: AuthUser, jamId: String, details: GameDetails, build: GameBuild): Game {
        // Check again: the jam can end or be deleted while the build is processed.
        checkCanUpload(user, jamId)
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

    @Transactional(readOnly = true)
    fun checkCanUpdate(user: AuthUser, gameId: String) {
        findManageable(user, gameId)
    }

    /** Updates the game. When [build] is given, the previous build folder is deleted after commit. */
    @Transactional
    fun update(user: AuthUser, gameId: String, details: GameDetails, build: GameBuild?): Game {
        val game = findManageable(user, gameId)
        val now = clock.nowMillis()
        game.updateDetails(details, now)
        if (build != null) {
            game.replaceBuild(build, now)?.let { previous -> afterCommit { buildStorage.delete(previous) } }
        }
        return game
    }

    private fun findManageable(user: AuthUser, gameId: String): Game {
        val game = gameRepository.findById(gameId).orElse(null)
        if (game == null || !game.isVisibleTo(user)) throw GmmException(ErrorCode.GAME_NOT_FOUND)
        if (!game.canManage(user)) throw GmmException(ErrorCode.GAME_NOT_OWNER)
        return game
    }
}
