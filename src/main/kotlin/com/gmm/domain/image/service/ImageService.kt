package com.gmm.domain.image.service

import com.gmm.domain.image.storage.ImageStorage
import com.gmm.global.config.GmmProperties
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

/**
 * Accepts JPEG, PNG and WebP only, detected from the file's leading bytes, never from its name
 * or the client's Content-Type. SVG is rejected because it can carry scripts.
 */
@Service
class ImageService(private val imageStorage: ImageStorage, properties: GmmProperties) {
    private val maxSize = properties.image.maxSize

    fun upload(file: MultipartFile): String {
        if (file.isEmpty) throw GmmException(ErrorCode.IMAGE_REQUIRED)
        if (file.size > maxSize.toBytes()) {
            // The limit comes from GMM_IMAGE_MAX_SIZE, so the message must not hardcode it.
            throw GmmException(ErrorCode.IMAGE_TOO_LARGE, "이미지는 ${maxSize.toMegabytes()}MB 이하만 올릴 수 있어요.")
        }
        val bytes = file.bytes
        val extension = detectExtension(bytes) ?: throw GmmException(ErrorCode.IMAGE_INVALID)
        return imageStorage.store(bytes, extension)
    }

    companion object {
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
