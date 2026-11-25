package com.gmm.domain.build

import com.gmm.domain.build.service.BuildLimits
import com.gmm.domain.build.service.BuildProcessor
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class BuildProcessorTest :
    FunSpec({
        val limits = BuildLimits(maxExtractedBytes = 1024 * 1024, maxFileCount = 100)
        val processor = BuildProcessor(limits)
        lateinit var dir: Path

        beforeEach { dir = Files.createTempDirectory("unity-build-test") }
        afterEach { dir.toFile().deleteRecursively() }

        fun process(entries: Map<String, ByteArray>, using: BuildProcessor = processor): Pair<String?, Path> {
            val out = dir.resolve("out")
            return using.process(ZipFixtures.write(dir, entries), out) to out
        }

        fun rejected(entries: Map<String, ByteArray>, using: BuildProcessor = processor): GmmException =
            shouldThrow<GmmException> { process(entries, using) }

        test("extracts a valid build and returns its build name") {
            val (name, out) = process(ZipFixtures.unityBuild("MiniHero"))

            name shouldBe "MiniHero"
            out.resolve("index.html").readText() shouldBe "<html></html>"
            out.resolve("Build/MiniHero.wasm").exists() shouldBe true
            out.resolve("StreamingAssets/config.json").exists() shouldBe true
        }

        test("ignores __MACOSX and dot files") {
            val entries =
                ZipFixtures.unityBuild() +
                    mapOf(
                        "__MACOSX/._index.html" to ByteArray(1),
                        ".DS_Store" to ByteArray(1),
                        "Build/.hidden" to ByteArray(1),
                    )

            val (_, out) = process(entries)

            out.resolve("__MACOSX").exists() shouldBe false
            out.resolve(".DS_Store").exists() shouldBe false
            out.resolve("Build/.hidden").exists() shouldBe false
        }

        test("rejects a file that is not a zip") {
            val notZip = Files.writeString(dir.resolve("build.zip"), "not a zip")

            val e = shouldThrow<GmmException> { processor.process(notZip, dir.resolve("out")) }

            e.code shouldBe ErrorCode.BUILD_INVALID
            e.message shouldContain "열 수 없어요"
        }

        test("rejects a zip of the whole build folder with a specific message") {
            rejected(ZipFixtures.unityBuild(prefix = "MiniHero/")).message shouldContain "폴더째 압축한"
        }

        test("rejects a zip without index.html") {
            rejected(ZipFixtures.unityBuild() - "index.html").message shouldContain "index.html이 있어야"
        }

        test("rejects a build with a missing file") {
            rejected(ZipFixtures.unityBuild() - "Build/MiniHero.wasm").message shouldContain "같은 이름으로"
        }

        test("rejects build files with different names") {
            val entries =
                ZipFixtures.unityBuild() - "Build/MiniHero.data" + ("Build/Other.data" to ByteArray(1))

            rejected(entries).message shouldContain "같은 이름으로"
        }

        test("rejects two complete builds") {
            val entries = ZipFixtures.unityBuild("A") + ZipFixtures.unityBuild("B")

            rejected(entries).message shouldContain "여러 개"
        }

        test("rejects compressed builds") {
            listOf(".gz", ".br", ".unityweb").forEach { suffix ->
                val entries =
                    ZipFixtures.unityBuild() - "Build/MiniHero.wasm" + ("Build/MiniHero.wasm$suffix" to ByteArray(1))

                rejected(entries).message shouldContain "Compression Format"
            }
        }

        test("extracts a web build with nested folders and returns no build name") {
            val (name, out) = process(ZipFixtures.webBuild())

            name shouldBe null
            out.resolve("game.wasm").exists() shouldBe true
            out.resolve("assets/game.pck").exists() shouldBe true
        }

        test("rejects a zip of the whole web build folder") {
            rejected(ZipFixtures.webBuild(prefix = "MyGame/")).message shouldContain "폴더째 압축한"
        }

        test("rejects compressed files anywhere in a web build") {
            listOf("game.wasm.gz", "assets/game.pck.br", "assets/GAME.JS.GZ").forEach { path ->
                rejected(ZipFixtures.webBuild() + (path to ByteArray(1))).message shouldContain "사전 압축"
            }
        }

        test("detects a Unity build from the loader call in index.html") {
            val entries = ZipFixtures.webBuild() + ("index.html" to "createUnityInstance(canvas)".toByteArray())

            rejected(entries).message shouldContain "같은 이름으로"
        }

        test("allows compressed files outside Build in a Unity build") {
            val (name, _) = process(ZipFixtures.unityBuild() + ("StreamingAssets/data.gz" to ByteArray(1)))

            name shouldBe "MiniHero"
        }

        test("rejects zip slip paths") {
            listOf(
                "../evil.txt",
                "Build/../../evil.txt",
                "/etc/passwd",
                "C:/evil.txt",
                "..\\evil.txt",
            ).forEach { path ->
                listOf(ZipFixtures.unityBuild(), ZipFixtures.webBuild()).forEach { build ->
                    rejected(build + (path to ByteArray(1))).message shouldContain "허용되지 않는 경로"
                }
            }
            dir.parent.resolve("evil.txt").exists() shouldBe false
        }

        test("rejects archives over the file count limit") {
            val small = BuildProcessor(limits.copy(maxFileCount = 5))

            rejected(ZipFixtures.unityBuild(), small).message shouldContain "파일이 너무 많아요"
        }

        test("rejects archives over the extracted size limit") {
            val small = BuildProcessor(limits.copy(maxExtractedBytes = 1000))
            val entries = ZipFixtures.unityBuild() + ("StreamingAssets/big.bin" to ByteArray(4000))

            rejected(entries, small).code shouldBe ErrorCode.BUILD_TOO_LARGE
        }

        test("reads CP949 entry names from Korean Windows zips") {
            val entries = ZipFixtures.unityBuild() + ("StreamingAssets/한글.txt" to "ok".toByteArray())
            val zip = ZipFixtures.write(dir, entries, Charset.forName("MS949"))

            processor.process(zip, dir.resolve("out"))

            dir.resolve("out/StreamingAssets/한글.txt").readText() shouldBe "ok"
        }
    })
