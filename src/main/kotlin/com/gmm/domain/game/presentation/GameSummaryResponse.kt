package com.gmm.domain.game.presentation

import com.fasterxml.jackson.annotation.JsonUnwrapped

/** `Game` plus values the server computes: owner and jam names, review count, and average score. */
data class GameSummaryResponse(
    @field:JsonUnwrapped
    val game: GameResponse,
    val ownerName: String,
    val jamName: String,
    val reviewCount: Long,
    /** Mean of the reviews' overall scores; null when the game has no reviews. */
    val average: Double?,
)
