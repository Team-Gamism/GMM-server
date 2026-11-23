package com.gmm.domain.jam.service

import com.gmm.domain.build.storage.BuildStorage
import com.gmm.domain.game.repository.GameRepository
import com.gmm.domain.jam.entity.Jam
import com.gmm.domain.jam.entity.JamDetails
import com.gmm.domain.jam.presentation.JamResponse
import com.gmm.domain.jam.repository.JamAwardRepository
import com.gmm.domain.jam.repository.JamRepository
import com.gmm.global.auth.AuthUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import com.gmm.global.support.Ids
import com.gmm.global.support.afterCommit
import com.gmm.global.support.nowMillis
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class JamService(
    private val jamRepository: JamRepository,
    private val jamAwardRepository: JamAwardRepository,
    private val gameRepository: GameRepository,
    private val buildStorage: BuildStorage,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun list(): List<JamResponse> {
        val jams = jamRepository.findAllByOrderByStartAtDesc()
        val awards = jamAwardRepository.findAllByJamIdIn(jams.map { it.id }).groupBy { it.jamId }
        return jams.map { JamResponse.from(it, awards[it.id].orEmpty()) }
    }

    @Transactional(readOnly = true)
    fun get(jamId: String): JamResponse = toResponse(find(jamId))

    @Transactional
    fun create(user: AuthUser, details: JamDetails): JamResponse {
        user.requireAdmin()
        val jam = jamRepository.save(
            Jam(
                id = Ids.new(),
                name = details.name,
                description = details.description,
                theme = details.theme,
                coverUrl = details.coverUrl,
                startAt = details.startAt,
                endAt = details.endAt,
                hostId = user.id,
                createdAt = clock.nowMillis(),
            ),
        )
        return JamResponse.from(jam, emptyList())
    }

    @Transactional
    fun update(user: AuthUser, jamId: String, details: JamDetails): JamResponse {
        user.requireAdmin()
        val jam = find(jamId)
        jam.update(details, clock.nowMillis())
        return toResponse(jam)
    }

    /**
     * Deletes the jam. The database cascades to its games, reviews and awards; the build
     * folders of those games are deleted after commit because file deletion cannot roll back.
     */
    @Transactional
    fun delete(user: AuthUser, jamId: String) {
        user.requireAdmin()
        val jam = find(jamId)
        val gameIds = gameRepository.findIdsByJamId(jam.id)
        jamRepository.delete(jam)
        afterCommit { gameIds.forEach(buildStorage::deleteGame) }
        log.info("Delete jam {} with {} games", jam.id, gameIds.size)
    }

    private fun find(jamId: String): Jam =
        jamRepository.findById(jamId).orElseThrow { GmmException(ErrorCode.JAM_NOT_FOUND) }

    private fun toResponse(jam: Jam): JamResponse =
        JamResponse.from(jam, jamAwardRepository.findAllByJamIdIn(listOf(jam.id)))
}
