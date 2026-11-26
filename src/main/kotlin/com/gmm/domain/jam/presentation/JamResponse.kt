package com.gmm.domain.jam.presentation

import com.gmm.domain.jam.entity.Jam
import com.gmm.domain.jam.entity.JamAward
import java.time.Instant

data class JamResponse(
    val id: String,
    val name: String,
    val description: String,
    val theme: String,
    val coverUrl: String?,
    val startAt: Instant,
    val endAt: Instant,
    val reviewDays: Int,
    val hostId: String,
    val createdAt: Instant,
    val announcedAt: Instant?,
    val awards: List<AwardResponse>,
) {
    data class AwardResponse(val gameId: String, val rank: Int, val title: String)

    companion object {
        fun from(jam: Jam, awards: List<JamAward>) = JamResponse(
            id = jam.id,
            name = jam.name,
            description = jam.description,
            theme = jam.theme,
            coverUrl = jam.coverUrl,
            startAt = jam.startAt,
            endAt = jam.endAt,
            reviewDays = jam.reviewDays,
            hostId = jam.hostId,
            createdAt = jam.createdAt,
            announcedAt = jam.announcedAt,
            awards = awards.sortedBy { it.rank }.map { AwardResponse(it.gameId, it.rank, it.title) },
        )
    }
}
