package com.gmm.domain.build

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipFixtures {
    fun unityBuild(name: String = "MiniHero", prefix: String = ""): Map<String, ByteArray> = mapOf(
        "${prefix}index.html" to "<html></html>".toByteArray(),
        "${prefix}Build/$name.loader.js" to "loader".toByteArray(),
        "${prefix}Build/$name.data" to ByteArray(16),
        "${prefix}Build/$name.framework.js" to "framework".toByteArray(),
        "${prefix}Build/$name.wasm" to ByteArray(32),
        "${prefix}StreamingAssets/config.json" to "{}".toByteArray(),
    )

    fun webBuild(prefix: String = ""): Map<String, ByteArray> = mapOf(
        "${prefix}index.html" to "<html><script src=\"game.js\"></script></html>".toByteArray(),
        "${prefix}game.js" to "start()".toByteArray(),
        "${prefix}game.wasm" to ByteArray(32),
        "${prefix}assets/game.pck" to ByteArray(16),
    )

    fun write(dir: Path, entries: Map<String, ByteArray>, charset: Charset = Charsets.UTF_8): Path {
        val zip = Files.createTempFile(dir, "build", ".zip")
        ZipOutputStream(Files.newOutputStream(zip), charset).use { out ->
            for ((name, bytes) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return zip
    }
}
