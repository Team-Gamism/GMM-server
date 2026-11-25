package com.gmm.domain.build.service

import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

data class BuildLimits(val maxExtractedBytes: Long, val maxFileCount: Int)

/**
 * Validates a game build zip and extracts it. The rules match the frontend upload check
 * (`src/lib/buildZip.ts`) and the user guide (`/guide`):
 *
 * 1. The file is a readable zip with `index.html` at its root (a zip of the whole folder is rejected).
 * 2. It is a Unity WebGL build when `Build/` holds a Unity loader, framework or `.unityweb` file, or the root
 *    `index.html` calls the Unity loader. Anything else is a web (HTML5/WebAssembly) build.
 * 3. Unity: `Build/{name}.loader.js`, `.data`, `.framework.js`, and `.wasm` share one `{name}`, the build name.
 *    Compressed files (`.gz`, `.br`, `.unityweb`) in `Build/` are rejected.
 * 4. Web: no other required file. Compressed files anywhere in the zip are rejected.
 * 5. `__MACOSX/` and names that start with `.` are ignored.
 * 6. Unsafe paths (zip slip) and archives over the size or file-count limit are rejected.
 */
class BuildProcessor(private val limits: BuildLimits) {
    /**
     * Validates [zip], extracts it into the empty or missing directory [outputDir], and returns the Unity build
     * name, or null for a web build.
     */
    fun process(zip: Path, outputDir: Path): String? = openZip(zip).use { zipFile ->
        val entries = collectEntries(zipFile)
        val buildName = inspect(zipFile, entries)
        extract(zipFile, entries, outputDir)
        buildName
    }

    private fun openZip(zip: Path): ZipFile {
        // Zips made on Korean Windows often store names in CP949 without the UTF-8 flag.
        for (charset in ENTRY_NAME_CHARSETS) {
            try {
                return ZipFile(zip.toFile(), charset).also { it.entries().toList() }
            } catch (_: ZipException) {
            } catch (_: IllegalArgumentException) {
            } catch (_: IOException) {
            }
        }
        throw invalid("zip 파일을 열 수 없어요. 압축이 올바른지 확인해 주세요.")
    }

    private fun collectEntries(zipFile: ZipFile): List<BuildEntry> {
        val entries = mutableListOf<BuildEntry>()
        val seen = HashSet<String>()
        var declaredBytes = 0L
        for (entry in zipFile.entries()) {
            val segments = safeSegments(entry.name)
            if (segments.isEmpty() || isIgnored(segments) || entry.isDirectory) continue
            val path = segments.joinToString("/")
            if (!seen.add(path)) throw invalid("zip 안에 같은 경로의 파일이 여러 개 있어요.")
            if (entries.size >= limits.maxFileCount) {
                throw invalid("zip 안의 파일이 너무 많아요. 최대 ${limits.maxFileCount}개까지 올릴 수 있어요.")
            }
            if (entry.size > 0) declaredBytes += entry.size
            if (declaredBytes > limits.maxExtractedBytes) throw tooLarge()
            entries += BuildEntry(entry, path)
        }
        return entries
    }

    private fun safeSegments(rawName: String): List<String> {
        val name = rawName.replace('\\', '/')
        if (name.startsWith("/") || DRIVE_LETTER.containsMatchIn(name) || name.contains('\u0000')) {
            throw unsafePath()
        }
        val segments = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.any { it == ".." }) throw unsafePath()
        return segments
    }

    private fun isIgnored(segments: List<String>): Boolean =
        segments.first() == "__MACOSX" || segments.any { it.startsWith(".") }

    private fun inspect(zipFile: ZipFile, entries: List<BuildEntry>): String? {
        val paths = entries.map { it.path }
        val index = entries.find { it.path == INDEX }
        if (index == null) {
            val topFolders = paths.mapNotNull { it.substringBefore('/', "").ifEmpty { null } }.toSet()
            if (topFolders.size == 1 && "${topFolders.first()}/$INDEX" in paths) {
                throw invalid("폴더째 압축한 zip이에요. 빌드 폴더 안의 파일들을 선택해서 압축해 주세요.")
            }
            throw invalid("zip 최상위에 index.html이 있어야 해요.")
        }

        val buildFiles = paths.filter { it.startsWith("Build/") }.map { it.removePrefix("Build/") }
        val isUnity =
            buildFiles.any { UNITY_FILE.containsMatchIn(it) } ||
                UNITY_LOADER_CALL.containsMatchIn(readStart(zipFile, index))
        if (isUnity) return inspectUnity(buildFiles.filter { '/' !in it })

        if (paths.any(::isCompressed)) {
            throw invalid("압축된 파일(.gz, .br)이 있어요. 엔진의 사전 압축 옵션을 끄고 다시 내보내 주세요.")
        }
        return null
    }

    private fun inspectUnity(buildFiles: List<String>): String {
        if (buildFiles.any(::isCompressed)) {
            throw invalid(
                "압축된 빌드는 올릴 수 없어요. Unity의 Publishing Settings에서 Compression Format을 Disabled로 바꿔 주세요.",
            )
        }

        val buildFileSet = buildFiles.toHashSet()
        val complete =
            buildFiles
                .filter { it.endsWith(LOADER_SUFFIX) }
                .map { it.removeSuffix(LOADER_SUFFIX) }
                .filter { name -> name.isNotEmpty() && OTHER_SUFFIXES.all { "$name$it" in buildFileSet } }
        return when (complete.size) {
            1 -> {
                complete.single().also {
                    if (it.length > MAX_BUILD_NAME_LENGTH) throw invalid("빌드 이름이 너무 길어요.")
                }
            }

            0 -> {
                throw invalid(
                    "Build 폴더에 .loader.js, .data, .framework.js, .wasm 파일이 같은 이름으로 있어야 해요.",
                )
            }

            else -> {
                throw invalid("Build 폴더에 빌드가 여러 개 있어요. 하나만 남기고 다시 압축해 주세요.")
            }
        }
    }

    private fun extract(zipFile: ZipFile, entries: List<BuildEntry>, outputDir: Path) {
        val root = outputDir.toAbsolutePath().normalize()
        Files.createDirectories(root)
        var writtenBytes = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            for (entry in entries) {
                val target = root.resolve(entry.path).normalize()
                if (!target.startsWith(root) || target == root) throw unsafePath()
                Files.createDirectories(target.parent)
                zipFile.getInputStream(entry.zipEntry).use { input ->
                    Files.newOutputStream(target, StandardOpenOption.CREATE_NEW).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            writtenBytes += read
                            // Declared sizes can lie, so the limit is enforced on the bytes actually written.
                            if (writtenBytes > limits.maxExtractedBytes) throw tooLarge()
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
        } catch (e: GmmException) {
            throw e
        } catch (_: IOException) {
            throw invalid("zip 파일을 풀 수 없어요. 압축이 올바른지 확인해 주세요.")
        }
    }

    private fun isCompressed(path: String) = COMPRESSED_SUFFIXES.any { path.endsWith(it, ignoreCase = true) }

    /** Only the start of `index.html`, enough to find the Unity loader call. */
    private fun readStart(zipFile: ZipFile, entry: BuildEntry): String = try {
        zipFile.getInputStream(entry.zipEntry).use { String(it.readNBytes(MAX_INDEX_BYTES), Charsets.UTF_8) }
    } catch (_: IOException) {
        throw invalid("zip 파일을 풀 수 없어요. 압축이 올바른지 확인해 주세요.")
    }

    private fun invalid(message: String) = GmmException(ErrorCode.BUILD_INVALID, message)

    private fun unsafePath() = invalid("zip 안에 허용되지 않는 경로가 있어요.")

    private fun tooLarge() = GmmException(
        ErrorCode.BUILD_TOO_LARGE,
        "압축을 푼 빌드 용량이 너무 커요. 최대 ${limits.maxExtractedBytes / (1024 * 1024)}MB까지 올릴 수 있어요.",
    )

    private class BuildEntry(val zipEntry: ZipEntry, val path: String)

    companion object {
        private const val INDEX = "index.html"
        private const val MAX_INDEX_BYTES = 1024 * 1024
        private val UNITY_FILE =
            Regex("""\.(loader\.js|framework\.js(\.gz|\.br|\.unityweb)?|unityweb)$""", RegexOption.IGNORE_CASE)
        private val UNITY_LOADER_CALL = Regex("createUnityInstance|UnityLoader")
        private const val LOADER_SUFFIX = ".loader.js"
        private val OTHER_SUFFIXES = listOf(".data", ".framework.js", ".wasm")
        private val COMPRESSED_SUFFIXES = listOf(".gz", ".br", ".unityweb")
        private val DRIVE_LETTER = Regex("^[A-Za-z]:")
        private val ENTRY_NAME_CHARSETS = listOf(Charsets.UTF_8, Charset.forName("MS949"))
        private const val MAX_BUILD_NAME_LENGTH = 200
        private const val BUFFER_SIZE = 64 * 1024
    }
}
