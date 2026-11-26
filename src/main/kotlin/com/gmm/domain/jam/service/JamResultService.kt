package com.gmm.domain.jam.service

import com.gmm.domain.game.repository.GameRepository
import com.gmm.domain.game.service.GameSummaries
import com.gmm.domain.jam.entity.Jam
import com.gmm.domain.jam.entity.JamAward
import com.gmm.domain.jam.entity.JamStatus
import com.gmm.domain.jam.presentation.AwardsRequest
import com.gmm.domain.jam.presentation.JamResponse
import com.gmm.domain.jam.presentation.RankingEntryResponse
import com.gmm.domain.jam.presentation.RankingEntryResponse.CriterionResponse
import com.gmm.domain.jam.repository.JamAwardRepository
import com.gmm.domain.jam.repository.JamRepository
import com.gmm.domain.review.repository.ReviewStats
import com.gmm.global.auth.AuthUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import com.gmm.global.support.nowMillis
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class JamResultService(
    private val jamRepository: JamRepository,
    private val jamAwardRepository: JamAwardRepository,
    private val gameRepository: GameRepository,
    private val gameSummaries: GameSummaries,
    private val clock: Clock,
) {
    /**
     * All games of the jam with overall and per-criterion ranks, sorted by overall rank.
     * Until the results are announced only admins see it, so scores do not leak while judging.
     */
    @Transactional(readOnly = true)
    fun ranking(user: AuthUser?, jamId: String): List<RankingEntryResponse> {
        val jam = find(jamId)
        if (jam.announcedAt == null && user?.isAdmin != true) throw GmmException(ErrorCode.RESULTS_NOT_ANNOUNCED)
        val games = gameRepository.findAllByJamIdOrderByCreatedAtAsc(jamId)
        val stats = gameSummaries.stats(games.map { it.id })
        val summaries = gameSummaries.summarize(games, stats)
        val statsOf = summaries.map { stats[it.game.id] }

        val overallRanks = RankingCalculator.rank(summaries.map { it.average })
        val criterionRanks = CRITERIA.mapValues { (_, average) ->
            RankingCalculator.rank(statsOf.map { it?.let(average) })
        }

        return summaries.indices
            .map { i ->
                RankingEntryResponse(
                    game = summaries[i],
                    overall = summaries[i].average,
                    criteria = CRITERIA.mapValues { (key, average) ->
                        CriterionResponse(statsOf[i]?.let(average), criterionRanks.getValue(key)[i])
                    },
                    rank = overallRanks[i],
                )
            }.sortedBy { it.rank }
    }

    /** Saves the awards (replacing earlier ones) and marks the jam announced. Allowed once the rating period ends. */
    @Transactional
    fun announce(user: AuthUser, jamId: String, request: AwardsRequest): JamResponse {
        user.requireAdmin()
        val jam = find(jamId)
        val status = jam.statusAt(clock.instant())
        if (status != JamStatus.JUDGING && status != JamStatus.ANNOUNCED) throw GmmException(ErrorCode.JAM_NOT_FINISHED)

        val awards = validate(jam, request)
        jamAwardRepository.deleteAllByJamId(jam.id)
        val saved = jamAwardRepository.saveAll(awards)
        jam.announce(clock.nowMillis())
        return JamResponse.from(jam, saved)
    }

    /** Hides the results again. Awards stay, as in the frontend mock, so a re-announcement can start from them. */
    @Transactional
    fun cancel(user: AuthUser, jamId: String): JamResponse {
        user.requireAdmin()
        val jam = find(jamId)
        jam.cancelAnnouncement(clock.nowMillis())
        return JamResponse.from(jam, jamAwardRepository.findAllByJamIdIn(listOf(jam.id)))
    }

    private fun validate(jam: Jam, request: AwardsRequest): List<JamAward> {
        val requested = request.awards ?: throw GmmException(ErrorCode.INVALID_AWARD)
        val gameIds = gameRepository.findIdsByJamId(jam.id).toSet()
        val seen = HashSet<String>()
        return requested.map { award ->
            val gameId = award.gameId?.trim().orEmpty()
            if (gameId !in gameIds) throw GmmException(ErrorCode.INVALID_AWARD, "수상작은 이 게임잼의 게임이어야 해요.")
            if (!seen.add(gameId)) throw GmmException(ErrorCode.INVALID_AWARD, "같은 게임에 상을 두 번 줄 수 없어요.")
            val rank = award.rank?.takeIf { it >= 1 } ?: throw GmmException(ErrorCode.INVALID_AWARD, "수상 순위가 올바르지 않아요.")
            val title = award.title?.trim().orEmpty()
            if (title.isEmpty()) throw GmmException(ErrorCode.INVALID_AWARD, "상 이름을 입력해 주세요.")
            if (title.length >
                TITLE_MAX
            ) {
                throw GmmException(ErrorCode.INVALID_AWARD, "상 이름은 ${TITLE_MAX}자 이하로 입력해 주세요.")
            }
            JamAward(jamId = jam.id, gameId = gameId, rank = rank, title = title)
        }
    }

    private fun find(jamId: String): Jam = jamRepository.findById(jamId).orElseThrow {
        GmmException(ErrorCode.JAM_NOT_FOUND)
    }

    companion object {
        const val TITLE_MAX = 50

        /** API keys of the four criteria and how to read their average. */
        private val CRITERIA: Map<String, (ReviewStats) -> Double?> = linkedMapOf(
            "fun" to { it.funAverage },
            "theme" to { it.themeAverage },
            "polish" to { it.polishAverage },
            "creativity" to { it.creativityAverage },
        )
    }
}
