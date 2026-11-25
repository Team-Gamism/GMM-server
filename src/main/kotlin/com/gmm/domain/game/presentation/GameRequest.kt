package com.gmm.domain.game.presentation

import com.gmm.domain.game.entity.GameDetails
import com.gmm.domain.game.entity.Genre
import com.gmm.domain.game.entity.InputDevice
import com.gmm.domain.game.entity.Orientation
import com.gmm.domain.game.entity.Platform
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException

/** The `data` part of `POST /games` and `PUT /games/{gameId}`. All fields are nullable so validation can explain what is missing. */
data class GameRequest(
    val jamId: String? = null,
    val title: String? = null,
    val tagline: String? = null,
    val description: String? = null,
    val genre: Genre? = null,
    val iconUrl: String? = null,
    val orientation: Orientation? = null,
    val env: EnvRequest? = null,
) {
    data class EnvRequest(
        val platforms: List<Platform>? = null,
        val inputs: List<InputDevice>? = null,
        val note: String? = null,
    )

    fun requireJamId(): String = jamId?.trim()?.takeIf { it.isNotEmpty() } ?: reject("게임잼을 선택해 주세요.")

    /** [isUploadedImage] accepts only URLs from the image upload API. */
    fun toDetails(isUploadedImage: (String) -> Boolean): GameDetails {
        val title = title?.trim().orEmpty()
        if (title.isEmpty()) reject("게임 제목을 입력해 주세요.")
        if (title.length > TITLE_MAX) reject("게임 제목은 ${TITLE_MAX}자 이하로 입력해 주세요.")

        val tagline = tagline?.trim().orEmpty()
        if (tagline.length > TAGLINE_MAX) reject("한 줄 소개는 ${TAGLINE_MAX}자 이하로 입력해 주세요.")

        val description = description.orEmpty()
        if (description.length > DESCRIPTION_MAX) reject("설명은 ${DESCRIPTION_MAX}자 이하로 입력해 주세요.")

        val platforms = env?.platforms.orEmpty().distinct()
        if (platforms.isEmpty()) reject("권장 플랫폼을 하나 이상 선택해 주세요.")

        val note = env?.note?.trim().orEmpty()
        if (note.length > NOTE_MAX) reject("권장 환경 메모는 ${NOTE_MAX}자 이하로 입력해 주세요.")

        return GameDetails(
            title = title,
            tagline = tagline,
            description = description,
            genre = genre ?: reject("장르를 선택해 주세요."),
            iconUrl = normalizeIconUrl(iconUrl, isUploadedImage),
            orientation = orientation ?: reject("화면 방향을 선택해 주세요."),
            platforms = platforms,
            inputs = env?.inputs.orEmpty().distinct(),
            note = note,
        )
    }

    private fun normalizeIconUrl(value: String?, isUploadedImage: (String) -> Boolean): String? {
        val url = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (url.length > URL_MAX || !isUploadedImage(url)) {
            reject("아이콘 주소가 올바르지 않아요. 이미지를 다시 올려 주세요.")
        }
        return url
    }

    private fun reject(message: String): Nothing = throw GmmException(ErrorCode.INVALID_GAME_INPUT, message)

    companion object {
        const val TITLE_MAX = 40
        const val TAGLINE_MAX = 80
        const val DESCRIPTION_MAX = 10_000
        const val NOTE_MAX = 500
        const val URL_MAX = 2048
    }
}
