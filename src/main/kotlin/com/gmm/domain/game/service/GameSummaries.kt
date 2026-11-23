package com.gmm.domain.game.service

import com.gmm.domain.build.storage.BuildStorage
import com.gmm.domain.game.entity.Game
import com.gmm.domain.game.presentation.GameResponse
import com.gmm.domain.game.presentation.GameSummaryResponse
import com.gmm.domain.jam.repository.JamRepository
import com.gmm.domain.review.repository.ReviewRepository
import com.gmm.domain.review.repository.ReviewStats
import com.gmm.domain.user.repository.UserRepository
import org.springframework.stereotype.Component

/**
 * Builds [GameSummaryResponse]s with a fixed number of queries (owners, jams, review stats),
 * whatever the number of games. Ranking and profiles reuse it.
 */
@Component
class GameSummaries(
    private val userRepository: UserRepository,
    private val jamRepository: JamRepository,
    private val reviewRepository: ReviewRepository,
    private val buildStorage: BuildStorage,
) {
    fun stats(gameIds: Collection<String>): Map<String, ReviewStats> =
        if (gameIds.isEmpty()) emptyMap() else reviewRepository.findStats(gameIds).associateBy { it.gameId }

    fun summarize(
        games: List<Game>,
        stats: Map<String, ReviewStats> = stats(
            games.map {
                it.id
            },
        ),
    ): List<GameSummaryResponse> {
        if (games.isEmpty()) return emptyList()
        val ownerNames = userRepository.findAllById(games.map { it.ownerId }.toSet()).associate { it.id to it.name }
        val jamNames = jamRepository.findAllById(games.map { it.jamId }.toSet()).associate { it.id to it.name }
        return games.map { game ->
            val stat = stats[game.id]
            GameSummaryResponse(
                game = GameResponse.from(game, buildStorage::publicUrl),
                ownerName = ownerNames[game.ownerId] ?: UNKNOWN,
                jamName = jamNames[game.jamId] ?: UNKNOWN,
                reviewCount = stat?.reviewCount ?: 0,
                average = stat?.average,
            )
        }
    }

    companion object {
        const val UNKNOWN = "알 수 없음"
    }
}
