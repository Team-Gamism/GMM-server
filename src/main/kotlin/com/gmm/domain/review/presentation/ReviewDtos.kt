package com.gmm.domain.review.presentation

import com.gmm.domain.review.entity.Review
import com.gmm.domain.review.entity.ReviewScores
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import java.time.Instant

/** Body of `PUT /games/{gameId}/reviews/me`. */
data class ReviewRequest(val scores: ScoresRequest? = null, val comment: String? = null) {
    data class ScoresRequest(
        val `fun`: Int? = null,
        val theme: Int? = null,
        val polish: Int? = null,
        val creativity: Int? = null,
    )

    fun toScores(): ReviewScores {
        val values = listOf(scores?.`fun`, scores?.theme, scores?.polish, scores?.creativity)
        if (values.any { it == null || it !in SCORE_RANGE }) throw GmmException(ErrorCode.INVALID_REVIEW_INPUT)
        val (f, t, p, c) = values.map { it!! }
        return ReviewScores(f, t, p, c)
    }

    fun toComment(): String {
        val comment = comment?.trim().orEmpty()
        if (comment.length < COMMENT_MIN) {
            throw GmmException(ErrorCode.INVALID_REVIEW_INPUT, "피드백은 ${COMMENT_MIN}자 이상 입력해 주세요.")
        }
        if (comment.length > COMMENT_MAX) {
            throw GmmException(ErrorCode.INVALID_REVIEW_INPUT, "피드백은 ${COMMENT_MAX}자 이하로 입력해 주세요.")
        }
        return comment
    }

    companion object {
        val SCORE_RANGE = 1..5
        const val COMMENT_MIN = 5
        const val COMMENT_MAX = 1000
    }
}

data class ScoresResponse(val `fun`: Int, val theme: Int, val polish: Int, val creativity: Int) {
    companion object {
        fun from(scores: ReviewScores) = ScoresResponse(scores.`fun`, scores.theme, scores.polish, scores.creativity)
    }
}

data class ReviewResponse(
    val id: String,
    val gameId: String,
    val authorId: String,
    val scores: ScoresResponse,
    val comment: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(review: Review) = ReviewResponse(
            id = review.id,
            gameId = review.gameId,
            authorId = review.authorId,
            scores = ScoresResponse.from(review.scores),
            comment = review.comment,
            createdAt = review.createdAt,
            updatedAt = review.updatedAt,
        )
    }
}

data class ReviewWithAuthorResponse(
    val id: String,
    val gameId: String,
    val authorId: String,
    val scores: ScoresResponse,
    val comment: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val authorName: String,
    val authorAvatarUrl: String?,
)
