package com.gmm.global.auth

import com.gmm.domain.user.entity.UserRole

/** The signed-in user for the current request. */
data class AuthUser(val id: String, val role: UserRole) {
    val isAdmin: Boolean get() = role == UserRole.ADMIN
}
