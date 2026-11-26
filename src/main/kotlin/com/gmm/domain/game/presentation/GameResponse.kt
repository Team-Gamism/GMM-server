package com.gmm.domain.game.presentation

import com.gmm.domain.game.entity.Game
import com.gmm.domain.game.entity.Genre
import com.gmm.domain.game.entity.InputDevice
import com.gmm.domain.game.entity.Orientation
import com.gmm.domain.game.entity.Platform
import java.time.Instant

data class GameResponse(
    val id: String,
    val jamId: String,
    val ownerId: String,
    val title: String,
    val tagline: String,
    val description: String,
    val genre: Genre,
    val iconUrl: String?,
    val orientation: Orientation,
    val env: EnvResponse,
    val buildUrl: String?,
    val buildName: String?,
    val buildFileName: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    data class EnvResponse(val platforms: List<Platform>, val inputs: List<InputDevice>, val note: String)

    companion object {
        fun from(game: Game, buildUrl: (String) -> String) = GameResponse(
            id = game.id,
            jamId = game.jamId,
            ownerId = game.ownerId,
            title = game.title,
            tagline = game.tagline,
            description = game.description,
            genre = game.genre,
            iconUrl = game.iconUrl,
            orientation = game.orientation,
            env = EnvResponse(game.envPlatforms, game.envInputs, game.envNote),
            buildUrl = game.buildPath?.let(buildUrl),
            buildName = game.buildName,
            buildFileName = game.buildFileName,
            createdAt = game.createdAt,
            updatedAt = game.updatedAt,
        )
    }
}
