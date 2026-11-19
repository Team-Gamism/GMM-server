package com.gmm.domain.session.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** Server-side login session. [id] is the SHA-256 hash of the cookie value, never the value itself. */
@Entity
@Table(name = "tb_session")
class UserSession(
    @Id
    val id: String,
    @Column(name = "user_id", nullable = false)
    val userId: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    fun isExpiredAt(now: Instant): Boolean = !now.isBefore(expiresAt)
}
