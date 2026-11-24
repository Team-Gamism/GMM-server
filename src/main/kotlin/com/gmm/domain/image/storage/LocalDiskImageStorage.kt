package com.gmm.domain.image.storage

import com.gmm.global.config.GmmProperties
import com.gmm.global.support.Ids
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Writes images to `{root}/images/{uuid}.{ext}`, served by the same nginx as builds at
 * `{publicBaseUrl}/images/...`. The file is written in `staging/` first and moved, so nginx
 * never serves a partly written image.
 */
@Component
class LocalDiskImageStorage(properties: GmmProperties) : ImageStorage {
    private val root: Path = properties.build.rootDir.toAbsolutePath().normalize()
    private val imagesDir: Path = root.resolve("images")
    private val stagingDir: Path = root.resolve("staging")
    private val publicBaseUrl = properties.build.publicBaseUrl.trimEnd('/')

    init {
        Files.createDirectories(imagesDir)
        Files.createDirectories(stagingDir)
    }

    override fun store(bytes: ByteArray, extension: String): String {
        require(extension in ALLOWED_EXTENSIONS) { "Unsupported image extension" }
        val fileName = "${Ids.new()}.$extension"
        val temp = stagingDir.resolve("$fileName.part")
        Files.write(temp, bytes)
        Files.move(temp, imagesDir.resolve(fileName), StandardCopyOption.ATOMIC_MOVE)
        return "$publicBaseUrl/images/$fileName"
    }

    companion object {
        val ALLOWED_EXTENSIONS = setOf("jpg", "png", "webp")
    }
}
