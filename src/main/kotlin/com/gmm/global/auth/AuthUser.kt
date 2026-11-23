package com.gmm.global.auth

import com.gmm.domain.user.entity.UserRole
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException

/** The signed-in user for the current request. */
data class AuthUser(val id: String, val role: UserRole) {
    val isAdmin: Boolean get() = role == UserRole.ADMIN

    fun requireAdmin() {
        if (!isAdmin) throw GmmException(ErrorCode.ADMIN_ONLY)
    }
}
