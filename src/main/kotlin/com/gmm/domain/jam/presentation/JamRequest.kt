package com.gmm.domain.jam.presentation

import com.gmm.domain.jam.entity.JamDetails
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Body of `POST /jams` and `PUT /jams/{jamId}`. All fields are nullable so validation can explain what is missing. */
data class JamRequest(
    val name: String? = null,
    val description: String? = null,
    val theme: String? = null,
    val coverUrl: String? = null,
    val startAt: Instant? = null,
    val endAt: Instant? = null,
) {
    /** [isUploadedImage] accepts only URLs from the image upload API. */
    fun toDetails(isUploadedImage: (String) -> Boolean): JamDetails {
        val name = name?.trim().orEmpty()
        if (name.isEmpty()) reject("게임잼 이름을 입력해 주세요.")
        if (name.length > NAME_MAX) reject("게임잼 이름은 ${NAME_MAX}자 이하로 입력해 주세요.")

        val theme = theme?.trim().orEmpty()
        if (theme.isEmpty()) reject("주제를 입력해 주세요.")
        if (theme.length > THEME_MAX) reject("주제는 ${THEME_MAX}자 이하로 입력해 주세요.")

        val description = description.orEmpty()
        if (description.length > DESCRIPTION_MAX) reject("설명은 ${DESCRIPTION_MAX}자 이하로 입력해 주세요.")

        val startAt = startAt?.truncatedTo(ChronoUnit.MILLIS) ?: reject("시작 시각을 입력해 주세요.")
        val endAt = endAt?.truncatedTo(ChronoUnit.MILLIS) ?: reject("종료 시각을 입력해 주세요.")
        if (!endAt.isAfter(startAt)) reject("종료 시각은 시작 시각보다 뒤여야 해요.")

        return JamDetails(
            name = name,
            description = description,
            theme = theme,
            coverUrl = normalizeCoverUrl(coverUrl, isUploadedImage),
            startAt = startAt,
            endAt = endAt,
        )
    }

    private fun normalizeCoverUrl(value: String?, isUploadedImage: (String) -> Boolean): String? {
        val url = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (url.length > URL_MAX || !isUploadedImage(url)) {
            reject("대표 이미지 주소가 올바르지 않아요. 이미지를 다시 올려 주세요.")
        }
        return url
    }

    private fun reject(message: String): Nothing = throw GmmException(ErrorCode.INVALID_JAM_INPUT, message)

    companion object {
        const val NAME_MAX = 100
        const val THEME_MAX = 100
        const val DESCRIPTION_MAX = 10_000
        const val URL_MAX = 2048
    }
}
