package com.gmm.domain.jam.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "tb_jam")
class Jam(
    @Id
    val id: String,
    name: String,
    description: String,
    theme: String,
    coverUrl: String?,
    startAt: Instant,
    endAt: Instant,
    reviewDays: Int,
    @Column(name = "host_id", nullable = false)
    val hostId: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    @Column(nullable = false)
    var name: String = name
        protected set

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = description
        protected set

    @Column(nullable = false)
    var theme: String = theme
        protected set

    @Column(name = "cover_url")
    var coverUrl: String? = coverUrl
        protected set

    @Column(name = "start_at", nullable = false)
    var startAt: Instant = startAt
        protected set

    @Column(name = "end_at", nullable = false)
    var endAt: Instant = endAt
        protected set

    /** Rating period after [endAt], in days. */
    @Column(name = "review_days", nullable = false)
    var reviewDays: Int = reviewDays
        protected set

    @Column(name = "announced_at")
    var announcedAt: Instant? = null
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    fun statusAt(now: Instant): JamStatus = JamStatus.of(startAt, endAt, reviewDays, announcedAt, now)

    fun announce(now: Instant) {
        announcedAt = now
        updatedAt = now
    }

    fun cancelAnnouncement(now: Instant) {
        announcedAt = null
        updatedAt = now
    }

    fun update(details: JamDetails, now: Instant) {
        name = details.name
        description = details.description
        theme = details.theme
        coverUrl = details.coverUrl
        startAt = details.startAt
        endAt = details.endAt
        reviewDays = details.reviewDays
        updatedAt = now
    }
}

data class JamDetails(
    val name: String,
    val description: String,
    val theme: String,
    val coverUrl: String?,
    val startAt: Instant,
    val endAt: Instant,
    val reviewDays: Int,
)
