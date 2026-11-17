package com.gmm.domain.build

import com.gmm.domain.build.storage.LocalDiskBuildStorage
import com.gmm.global.config.GmmProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.io.path.exists

class LocalDiskBuildStorageTest :
    FunSpec({
        lateinit var root: Path
        lateinit var storage: LocalDiskBuildStorage

        beforeEach {
            root = Files.createTempDirectory("build-storage-test")
            storage =
                LocalDiskBuildStorage(
                    GmmProperties(build = GmmProperties.Build(rootDir = root, publicBaseUrl = "https://games.test/")),
                )
        }
        afterEach { root.toFile().deleteRecursively() }

        fun uuid() = UUID.randomUUID().toString()

        fun publishBuild(gameId: String, uploadId: String): String {
            val workspace = storage.createWorkspace(uploadId)
            val extracted = Files.createDirectories(workspace.resolve("build"))
            Files.writeString(extracted.resolve("index.html"), "x")
            return storage.publish(extracted, gameId, uploadId).also { storage.deleteWorkspace(workspace) }
        }

        test("publishes into builds/{gameId}/{uploadId} and builds the public URL") {
            val gameId = uuid()
            val uploadId = uuid()

            val path = publishBuild(gameId, uploadId)

            path shouldBe "$gameId/$uploadId"
            root.resolve("builds/$path/index.html").exists() shouldBe true
            root.resolve("staging/$uploadId").exists() shouldBe false
            storage.publicUrl(path) shouldBe "https://games.test/builds/$gameId/$uploadId"
        }

        test("deletes one build and a whole game") {
            val gameId = uuid()
            val first = publishBuild(gameId, uuid())
            val second = publishBuild(gameId, uuid())

            storage.delete(first)
            root.resolve("builds/$first").exists() shouldBe false
            root.resolve("builds/$second").exists() shouldBe true

            storage.deleteGame(gameId)
            root.resolve("builds/$gameId").exists() shouldBe false
        }

        test("refuses build paths that are not UUID pairs") {
            shouldThrow<IllegalArgumentException> { storage.delete("../../etc") }
            shouldThrow<IllegalArgumentException> { storage.deleteGame("..") }
            shouldThrow<IllegalArgumentException> { storage.publicUrl("a/b") }
        }

        test("deletes only unreferenced builds older than the threshold") {
            val gameId = uuid()
            val kept = publishBuild(gameId, uuid())
            val orphan = publishBuild(gameId, uuid())
            val fresh = publishBuild(gameId, uuid())
            val old = FileTime.from(Instant.now().minus(1, ChronoUnit.DAYS))
            Files.setLastModifiedTime(root.resolve("builds/$kept"), old)
            Files.setLastModifiedTime(root.resolve("builds/$orphan"), old)

            val deleted = storage.deleteOrphans(Instant.now().minus(1, ChronoUnit.HOURS)) { it == kept }

            deleted shouldBe 1
            root.resolve("builds/$kept").exists() shouldBe true
            root.resolve("builds/$orphan").exists() shouldBe false
            root.resolve("builds/$fresh").exists() shouldBe true
        }
    })
