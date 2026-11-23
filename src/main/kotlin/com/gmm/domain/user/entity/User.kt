package com.gmm.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "tb_user")
class User(
    @Id
    val id: String,
    @Column(name = "datagsm_id", nullable = false, unique = true)
    val dataGsmId: Long,
    email: String,
    name: String,
    studentNumber: String?,
    role: UserRole,
    createdAt: Instant,
) {
    @Column(nullable = false)
    var email: String = email
        protected set

    /** DataGSM student.name. Users cannot change it; every login refreshes it. */
    @Column(nullable = false)
    var name: String = name
        protected set

    @Column(name = "student_number")
    var studentNumber: String? = studentNumber
        protected set

    /** DataGSM has no generation value yet, so it stays null until a source is decided. */
    @Column
    var generation: Int? = null
        protected set

    @Column(name = "avatar_url")
    var avatarUrl: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: UserRole = role
        protected set

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = createdAt

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    fun refreshProfile(email: String, name: String, studentNumber: String?, now: Instant) {
        this.email = email
        this.name = name
        this.studentNumber = studentNumber
        this.updatedAt = now
    }

    /** The only field a user can change. Null resets to the default avatar. */
    fun changeAvatar(avatarUrl: String?, now: Instant) {
        this.avatarUrl = avatarUrl
        updatedAt = now
    }

    /** Returns true when the role changed. */
    fun assignRole(role: UserRole, now: Instant): Boolean {
        if (this.role == role) return false
        this.role = role
        updatedAt = now
        return true
    }

    val isAdmin: Boolean get() = role == UserRole.ADMIN
}
