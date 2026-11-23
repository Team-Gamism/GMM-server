package com.gmm.domain.jam.presentation

import com.gmm.domain.game.presentation.GameSummaryResponse

data class RankingEntryResponse(
    val game: GameSummaryResponse,
    /** Same as `game.average`. */
    val overall: Double?,
    val criteria: Map<String, CriterionResponse>,
    val rank: Int,
) {
    data class CriterionResponse(val average: Double?, val rank: Int)
}

data class AwardsRequest(val awards: List<AwardRequest>? = null) {
    data class AwardRequest(val gameId: String? = null, val rank: Int? = null, val title: String? = null)
}
