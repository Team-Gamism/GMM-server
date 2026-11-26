package com.gmm.domain.jam.entity

import java.time.Duration
import java.time.Instant

/** Derived on every read, never stored. Mirrors the frontend's `getJamStatus` in `src/lib/jam.ts`. */
enum class JamStatus {
    UPCOMING,
    ONGOING,
    REVIEWING,
    JUDGING,
    ANNOUNCED,
    ;

    val acceptsUploads: Boolean get() = this == ONGOING
    val acceptsReviews: Boolean get() = this == REVIEWING

    /** Submissions are closed, so every game of the jam is public. */
    val isPublic: Boolean get() = this != UPCOMING && this != ONGOING

    companion object {
        fun of(startAt: Instant, endAt: Instant, reviewDays: Int, announcedAt: Instant?, now: Instant): JamStatus =
            when {
                announcedAt != null -> ANNOUNCED
                now.isBefore(startAt) -> UPCOMING
                !now.isAfter(endAt) -> ONGOING
                !now.isAfter(endAt.plus(Duration.ofDays(reviewDays.toLong()))) -> REVIEWING
                else -> JUDGING
            }
    }
}
