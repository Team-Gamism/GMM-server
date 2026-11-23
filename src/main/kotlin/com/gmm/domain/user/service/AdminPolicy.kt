package com.gmm.domain.user.service

import com.gmm.domain.user.entity.UserRole
import com.gmm.global.config.GmmProperties
import org.springframework.stereotype.Component

/**
 * `gmm.admin.emails` is the only source of the admin role: listed emails are admins and
 * everyone else is a user. Removing an email takes effect on that user's next request.
 */
@Component
class AdminPolicy(properties: GmmProperties) {
    private val adminEmails: Set<String> =
        properties.admin.emails.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    fun roleFor(email: String): UserRole =
        if (email.trim().lowercase() in adminEmails) UserRole.ADMIN else UserRole.USER
}
