package com.gmm.domain.review.repository

/**
 * Review aggregates of one game. [average] is the mean of each review's overall score,
 * where a review's overall score is the mean of its four criteria.
 */
data class ReviewStats(
    val gameId: String,
    val reviewCount: Long,
    val average: Double?,
    val funAverage: Double?,
    val themeAverage: Double?,
    val polishAverage: Double?,
    val creativityAverage: Double?,
)
