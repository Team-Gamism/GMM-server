package com.gmm.domain.review.service

import com.gmm.domain.game.entity.Game
import com.gmm.domain.game.repository.GameRepository
import com.gmm.domain.jam.repository.JamRepository
import com.gmm.domain.review.entity.Review
import com.gmm.domain.review.presentation.ReviewRequest
import com.gmm.domain.review.presentation.ReviewResponse
import com.gmm.domain.review.presentation.ReviewWithAuthorResponse
import com.gmm.domain.review.presentation.ScoresResponse
import com.gmm.domain.review.repository.ReviewRepository
import com.gmm.domain.user.repository.UserRepository
import com.gmm.global.auth.AuthUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import com.gmm.global.support.Ids
import com.gmm.global.support.nowMillis
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val gameRepository: GameRepository,
    private val jamRepository: JamRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(user: AuthUser?, gameId: String): List<ReviewWithAuthorResponse> {
        findVisibleGame(user, gameId)
        val reviews = reviewRepository.findAllByGameIdOrderByCreatedAtDesc(gameId)
        val authors = userRepository.findAllById(reviews.map { it.authorId }.toSet()).associateBy { it.id }
        return reviews.map { review ->
            val author = authors[review.authorId]
            ReviewWithAuthorResponse(
                id = review.id,
                gameId = review.gameId,
                authorId = review.authorId,
                scores = ScoresResponse.from(review.scores),
                comment = review.comment,
                createdAt = review.createdAt,
                updatedAt = review.updatedAt,
                authorName = author?.name ?: UNKNOWN_AUTHOR,
                authorAvatarUrl = author?.avatarUrl,
            )
        }
    }

    /** One review per user per game: creates it the first time, then updates it. */
    @Transactional
    fun upsertMine(user: AuthUser, gameId: String, request: ReviewRequest): ReviewResponse {
        val game = findVisibleGame(user, gameId)
        if (game.isOwnedBy(user)) throw GmmException(ErrorCode.REVIEW_OWN_GAME)
        val jam = jamRepository.findById(game.jamId).orElseThrow { GmmException(ErrorCode.GAME_NOT_FOUND) }
        if (!jam.statusAt(clock.instant()).acceptsReviews) throw GmmException(ErrorCode.REVIEW_NOT_OPEN)
        val scores = request.toScores()
        val comment = request.toComment()

        val now = clock.nowMillis()
        val existing = reviewRepository.findByGameIdAndAuthorId(gameId, user.id)
        val review =
            if (existing != null) {
                existing.also { it.update(scores, comment, now) }
            } else {
                // saveAndFlush surfaces a lost unique-constraint race here, as 409 CONFLICT.
                reviewRepository.saveAndFlush(Review(Ids.new(), gameId, user.id, scores, comment, now))
            }
        return ReviewResponse.from(review)
    }

    /** Deleting a review that does not exist succeeds, as in the frontend mock. */
    @Transactional
    fun delete(user: AuthUser, reviewId: String) {
        val review = reviewRepository.findById(reviewId).orElse(null) ?: return
        if (review.authorId != user.id && !user.isAdmin) throw GmmException(ErrorCode.REVIEW_NOT_AUTHOR)
        reviewRepository.delete(review)
    }

    private fun findVisibleGame(user: AuthUser?, gameId: String): Game {
        val game = gameRepository.findById(gameId).orElse(null)
        if (game == null || !game.isVisibleTo(user)) throw GmmException(ErrorCode.GAME_NOT_FOUND)
        return game
    }

    companion object {
        const val UNKNOWN_AUTHOR = "알 수 없음"
    }
}
