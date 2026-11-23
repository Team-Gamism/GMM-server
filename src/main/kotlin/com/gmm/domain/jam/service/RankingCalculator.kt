package com.gmm.domain.jam.service

/**
 * Standard competition ranking, the same as the frontend mock's `getRanking`:
 * higher score first, equal scores share a rank and the next rank is skipped (1, 2, 2, 4).
 * Missing scores (no reviews) go last and share one rank.
 */
object RankingCalculator {
    /** Returns the rank of each item, in the order of [scores]. Ties keep the input order. */
    fun rank(scores: List<Double?>): List<Int> {
        val order = scores.indices.sortedWith(compareByDescending<Int> { scores[it] ?: MISSING })
        val ranks = IntArray(scores.size)
        order.forEachIndexed { position, index ->
            val previous = order.getOrNull(position - 1)
            ranks[index] =
                if (previous != null && sameScore(scores[previous], scores[index])) ranks[previous] else position + 1
        }
        return ranks.toList()
    }

    private fun sameScore(a: Double?, b: Double?): Boolean = (a ?: MISSING) == (b ?: MISSING)

    /** Below every real score (1 to 5), like the mock's `?? -1`. */
    private const val MISSING = -1.0
}
