package com.gmm.domain.image

import com.gmm.domain.image.service.ImageService
import com.gmm.domain.image.storage.LocalDiskImageStorage
import com.gmm.global.config.GmmProperties
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.springframework.mock.web.MockMultipartFile
import org.springframework.util.unit.DataSize
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes

class ImageServiceTest :
    FunSpec({
        lateinit var root: Path
        lateinit var service: ImageService

        beforeEach {
            root = Files.createTempDirectory("image-test")
            val properties =
                GmmProperties(
                    build = GmmProperties.Build(rootDir = root, publicBaseUrl = "https://games.test/"),
                    image = GmmProperties.Image(maxSize = DataSize.ofBytes(64)),
                )
            service = ImageService(LocalDiskImageStorage(properties), properties)
        }
        afterEach { root.toFile().deleteRecursively() }

        fun file(bytes: ByteArray, name: String = "a.png", type: String = "image/png") =
            MockMultipartFile("file", name, type, bytes)

        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2)
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2)
        val webp = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBPVP8 ".toByteArray()

        test("detects the type from the bytes, not from the name") {
            ImageService.detectExtension(jpeg) shouldBe "jpg"
            ImageService.detectExtension(png) shouldBe "png"
            ImageService.detectExtension(webp) shouldBe "webp"
            ImageService.detectExtension("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray()) shouldBe null
            ImageService.detectExtension("GIF89a".toByteArray()) shouldBe null
            ImageService.detectExtension(byteArrayOf(0xFF.toByte())) shouldBe null
        }

        test("stores the image under images/ and returns its public URL") {
            val url = service.upload(file(jpeg, name = "photo.png", type = "image/png"))

            url shouldMatch Regex("^https://games\\.test/images/[0-9a-f-]{36}\\.jpg$")
            val stored = root.resolve("images").listDirectoryEntries().single()
            stored.readBytes().toList() shouldBe jpeg.toList()
            root.resolve("staging").listDirectoryEntries() shouldBe emptyList()
        }

        test("rejects empty, oversized, and non-image files") {
            shouldThrow<GmmException> { service.upload(file(ByteArray(0))) }.code shouldBe ErrorCode.IMAGE_REQUIRED
            shouldThrow<GmmException> { service.upload(file(png + ByteArray(64))) }.code shouldBe
                ErrorCode.IMAGE_TOO_LARGE
            shouldThrow<GmmException> {
                service.upload(file("<svg onload=alert(1)>".toByteArray(), name = "x.png"))
            }.code shouldBe ErrorCode.IMAGE_INVALID
        }
    })
