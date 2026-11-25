package com.gmm.domain.image.service

import com.gmm.domain.image.storage.ImageStorage
import com.gmm.global.auth.AuthUser
import com.gmm.global.config.GmmProperties
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Accepts JPEG, PNG and WebP only, detected from the file's leading bytes, never from its name
 * or the client's Content-Type. SVG is rejected because it can carry scripts.
 *
 * Participants upload at most [DAILY_LIMIT] images a day (Korean date), so one account cannot fill the disk.
 * Admins have no limit.
 */
@Service
class ImageService(private val imageStorage: ImageStorage, properties: GmmProperties, clock: Clock) {
    private val maxSize = properties.image.maxSize
    private val koreanClock = clock.withZone(ZoneId.of("Asia/Seoul"))

    // ponytail: in-memory counter for one server; a restart resets it. Move it to the database if the API scales out.
    private var countedDay: LocalDate? = null
    private val uploadsToday = HashMap<String, Int>()

    fun upload(user: AuthUser, file: MultipartFile): String {
        if (file.isEmpty) throw GmmException(ErrorCode.IMAGE_REQUIRED)
        if (file.size > maxSize.toBytes()) {
            // The limit comes from GMM_IMAGE_MAX_SIZE, so the message must not hardcode it.
            throw GmmException(ErrorCode.IMAGE_TOO_LARGE, "이미지는 ${maxSize.toMegabytes()}MB 이하만 올릴 수 있어요.")
        }
        val bytes = file.bytes
        val extension = detectExtension(bytes) ?: throw GmmException(ErrorCode.IMAGE_INVALID)
        if (!user.isAdmin) countUpload(user.id)
        return imageStorage.store(bytes, extension)
    }

    @Synchronized
    private fun countUpload(userId: String) {
        val today = LocalDate.now(koreanClock)
        if (today != countedDay) {
            uploadsToday.clear()
            countedDay = today
        }
        val used = uploadsToday.getOrDefault(userId, 0)
        if (used >= DAILY_LIMIT) throw GmmException(ErrorCode.IMAGE_DAILY_LIMIT)
        uploadsToday[userId] = used + 1
    }

    companion object {
        const val DAILY_LIMIT = 50
        private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val RIFF = "RIFF".toByteArray()
        private val WEBP = "WEBP".toByteArray()

        fun detectExtension(bytes: ByteArray): String? = when {
            bytes.startsWith(JPEG) -> "jpg"
            bytes.startsWith(PNG) -> "png"
            bytes.startsWith(RIFF) && bytes.startsWith(WEBP, offset = 8) -> "webp"
            else -> null
        }

        private fun ByteArray.startsWith(prefix: ByteArray, offset: Int = 0): Boolean =
            size >= offset + prefix.size && prefix.indices.all { this[offset + it] == prefix[it] }
    }
}
