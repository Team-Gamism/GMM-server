package com.gmm.domain.game.service

import com.gmm.domain.game.entity.Game
import com.gmm.domain.game.entity.Genre
import com.gmm.domain.game.entity.Orientation
import com.gmm.domain.game.entity.ReviewStatus
import com.gmm.domain.game.presentation.GameSummaryResponse
import com.gmm.domain.game.repository.GameRepository
import com.gmm.global.auth.AuthUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale

data class GameQuery(
    val jamId: String? = null,
    val ownerId: String? = null,
    val genre: Genre? = null,
    val orientation: Orientation? = null,
    val q: String? = null,
    val sort: GameSort = GameSort.RECENT,
    val includeUnapproved: Boolean = false,
)

enum class GameSort { RECENT, RATING }

/**
 * MVP has no pagination. Jam and owner filters run in the database; visibility, genre, orientation and
 * search run in memory on that result, because search also matches the owner name and genre label.
 */
@Service
class GameQueryService(private val gameRepository: GameRepository, private val gameSummaries: GameSummaries) {
    @Transactional(readOnly = true)
    fun list(user: AuthUser?, query: GameQuery): List<GameSummaryResponse> {
        // Unapproved games are mixed in only on request (own games list or admin review), and only those the caller may see.
        val withUnapproved = query.ownerId != null || query.includeUnapproved
        val games =
            gameRepository.findAllByJamAndOwner(query.jamId, query.ownerId).filter { game ->
                (game.reviewStatus == ReviewStatus.APPROVED || (withUnapproved && game.isVisibleTo(user))) &&
                    (query.genre == null || game.genre == query.genre) &&
                    (query.orientation == null || game.orientation == query.orientation)
            }
        val keyword = query.q?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        val summaries = gameSummaries.summarize(games).filter { keyword == null || matches(it, keyword) }
        val recent = compareByDescending<GameSummaryResponse> { it.game.createdAt }
        return when (query.sort) {
            GameSort.RECENT -> summaries.sortedWith(recent)

            GameSort.RATING -> summaries.sortedWith(
                compareBy<GameSummaryResponse> { it.average == null }
                    .thenByDescending { it.average }
                    .then(recent),
            )
        }
    }

    @Transactional(readOnly = true)
    fun get(user: AuthUser?, gameId: String): GameSummaryResponse {
        val game = findVisible(user, gameId)
        return gameSummaries.summarize(listOf(game)).single()
    }

    private fun findVisible(user: AuthUser?, gameId: String): Game {
        val game = gameRepository.findById(gameId).orElse(null)
        if (game == null || !game.isVisibleTo(user)) throw GmmException(ErrorCode.GAME_NOT_FOUND)
        return game
    }

    private fun matches(summary: GameSummaryResponse, keyword: String): Boolean =
        listOf(summary.game.title, summary.game.tagline, summary.ownerName, summary.game.genre.label)
            .any { it.lowercase(Locale.ROOT).contains(keyword) }
}
