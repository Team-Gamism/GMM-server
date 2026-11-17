package com.gmm.domain.user.presentation

import com.gmm.domain.user.entity.User
import com.gmm.domain.user.entity.UserRole

data class UserResponse(
    val id: String,
    val name: String,
    val studentNumber: String?,
    val generation: Int?,
    val avatarUrl: String?,
    val role: UserRole,
    /** Discord notifications are out of MVP scope. The server returns the frontend mock's defaults and stores nothing. */
    val discord: DiscordSettingsResponse = DiscordSettingsResponse(),
) {
    data class DiscordSettingsResponse(
        val userId: String = "",
        val notifyUploadReview: Boolean = true,
        val notifyResults: Boolean = true,
        val notifyFeedback: Boolean = false,
    )

    companion object {
        fun from(user: User) = UserResponse(
            id = user.id,
            name = user.name,
            studentNumber = user.studentNumber,
            generation = user.generation,
            avatarUrl = user.avatarUrl,
            role = user.role,
        )
    }
}
