package com.gmm.domain.user.service

import com.gmm.domain.user.entity.UserRole
import com.gmm.global.config.GmmProperties
import org.springframework.stereotype.Component

/**
 * Emails in `gmm.admin.emails` are owners: always admins, and the only users who can grant or
 * revoke admin for others. Everyone else keeps the role stored in the DB.
 */
@Component
class AdminPolicy(properties: GmmProperties) {
    private val ownerEmails: Set<String> =
        properties.admin.emails.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    fun isOwner(email: String): Boolean = email.trim().lowercase() in ownerEmails

    fun roleFor(email: String, stored: UserRole): UserRole = if (isOwner(email)) UserRole.ADMIN else stored
}
