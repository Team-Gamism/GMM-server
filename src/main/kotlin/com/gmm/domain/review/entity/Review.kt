package com.gmm.domain.review.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** One user's rating of one game. Each score is an integer from 1 to 5. */
@Entity
@Table(name = "tb_review")
class Review(
    @Id
    val id: String,
    @Column(name = "game_id", nullable = false)
    val gameId: String,
    @Column(name = "author_id", nullable = false)
    val authorId: String,
    scores: ReviewScores,
    comment: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    @Column(name = "score_fun", nullable = false)
    var scoreFun: Int = scores.`fun`
        protected set

    @Column(name = "score_theme", nullable = false)
    var scoreTheme: Int = scores.theme
        protected set

    @Column(name = "score_polish", nullable = false)
    var scorePolish: Int = scores.polish
        protected set

    @Column(name = "score_creativity", nullable = false)
    var scoreCreativity: Int = scores.creativity
        protected set

    @Column(nullable = false)
    var comment: String = comment
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    val scores: ReviewScores get() = ReviewScores(scoreFun, scoreTheme, scorePolish, scoreCreativity)

    fun update(scores: ReviewScores, comment: String, now: Instant) {
        scoreFun = scores.`fun`
        scoreTheme = scores.theme
        scorePolish = scores.polish
        scoreCreativity = scores.creativity
        this.comment = comment
        updatedAt = now
    }
}

data class ReviewScores(val `fun`: Int, val theme: Int, val polish: Int, val creativity: Int)
