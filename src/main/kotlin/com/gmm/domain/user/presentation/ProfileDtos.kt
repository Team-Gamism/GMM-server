package com.gmm.domain.user.presentation

import com.gmm.domain.game.presentation.GameSummaryResponse
import com.gmm.domain.user.entity.UserRole
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException

/** Public profile. It never carries the student number or Discord settings. */
data class UserProfileResponse(
    val id: String,
    val name: String,
    val generation: Int?,
    val avatarUrl: String?,
    val role: UserRole,
    /** Approved entries. */
    val entryCount: Int,
    /** Awards with rank 1 in announced jams. Lower ranks show only in [awards]. */
    val winCount: Int,
    val games: List<GameSummaryResponse>,
    val awards: List<ProfileAwardResponse>,
)

data class ProfileAwardResponse(
    val gameId: String,
    val rank: Int,
    val title: String,
    val jamId: String,
    val jamName: String,
    val gameTitle: String,
)

/** Body of `PUT /users/me/avatar`. Null resets to the default avatar. */
data class AvatarRequest(val avatarUrl: String? = null) {
    fun normalized(): String? {
        val url = avatarUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (url.length > URL_MAX || !(url.startsWith("https://") || url.startsWith("http://"))) {
            throw GmmException(ErrorCode.INVALID_AVATAR)
        }
        return url
    }

    companion object {
        const val URL_MAX = 2048
    }
}
