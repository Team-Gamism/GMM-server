package com.gmm.domain.jam.entity

import java.time.Instant

/** Derived on every read, never stored. Mirrors the frontend's `src/lib/jam.ts`. */
enum class JamStatus {
    UPCOMING,
    ONGOING,
    JUDGING,
    ANNOUNCED,
    ;

    val acceptsUploads: Boolean get() = this == ONGOING
    val acceptsReviews: Boolean get() = this == ONGOING || this == JUDGING

    companion object {
        fun of(startAt: Instant, endAt: Instant, announcedAt: Instant?, now: Instant): JamStatus = when {
            announcedAt != null -> ANNOUNCED
            now.isBefore(startAt) -> UPCOMING
            !now.isAfter(endAt) -> ONGOING
            else -> JUDGING
        }
    }
}
