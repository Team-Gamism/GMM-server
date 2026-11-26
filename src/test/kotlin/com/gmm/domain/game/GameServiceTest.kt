package com.gmm.domain.game

import com.gmm.domain.build.storage.BuildStorage
import com.gmm.domain.game.entity.Game
import com.gmm.domain.game.entity.GameBuild
import com.gmm.domain.game.entity.GameDetails
import com.gmm.domain.game.entity.Genre
import com.gmm.domain.game.entity.Orientation
import com.gmm.domain.game.entity.Platform
import com.gmm.domain.game.repository.GameRepository
import com.gmm.domain.game.service.GameService
import com.gmm.domain.jam.entity.Jam
import com.gmm.domain.jam.repository.JamRepository
import com.gmm.domain.user.entity.UserRole
import com.gmm.global.auth.AuthUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import jakarta.persistence.Version
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class GameServiceTest :
    FunSpec({
        val now = Instant.parse("2026-11-19T00:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val gameRepository = mockk<GameRepository>()
        val jamRepository = mockk<JamRepository>()
        val buildStorage = mockk<BuildStorage>(relaxed = true)
        val service = GameService(gameRepository, jamRepository, buildStorage, clock)

        val owner = AuthUser("owner", UserRole.USER)
        val other = AuthUser("other", UserRole.USER)
        val admin = AuthUser("admin", UserRole.ADMIN)
        val details =
            GameDetails("제목", "", "", Genre.ACTION, null, Orientation.LANDSCAPE, listOf(Platform.PC), emptyList(), "")

        fun jam(start: String, end: String) =
            Jam("jam", "잼", "", "주제", null, Instant.parse(start), Instant.parse(end), 7, "admin", now)

        fun game() = Game("game", "jam", owner.id, details, GameBuild("g/old", "Old", "old.zip"), now.minusSeconds(60))

        test("allows uploads to an ongoing jam") {
            every { jamRepository.findById("jam") } returns
                Optional.of(jam("2026-11-18T00:00:00Z", "2026-11-20T00:00:00Z"))

            service.checkCanUpload(owner, "jam")
        }

        test("rejects participant uploads outside the submission window but allows admins") {
            every { jamRepository.findById("jam") } returns
                Optional.of(jam("2026-11-10T00:00:00Z", "2026-11-12T00:00:00Z"))

            shouldThrow<GmmException> { service.checkCanUpload(owner, "jam") }.code shouldBe ErrorCode.JAM_NOT_ONGOING
            service.checkCanUpload(admin, "jam")
        }

        test("participants create one game per jam; admins have no limit") {
            every { jamRepository.findById("jam") } returns
                Optional.of(jam("2026-11-18T00:00:00Z", "2026-11-20T00:00:00Z"))
            every { gameRepository.countByJamIdAndOwnerId("jam", owner.id) } returns 0
            every { gameRepository.countByJamIdAndOwnerId("jam", admin.id) } returns 3
            service.checkCanCreate(owner, "jam")
            service.checkCanCreate(admin, "jam")

            every { gameRepository.countByJamIdAndOwnerId("jam", owner.id) } returns 1
            shouldThrow<GmmException> { service.checkCanCreate(owner, "jam") }.code shouldBe
                ErrorCode.GAME_LIMIT_REACHED
        }

        test("create locks the jam and checks the limit again before saving") {
            val ongoing = jam("2026-11-18T00:00:00Z", "2026-11-20T00:00:00Z")
            every { jamRepository.findForUpdate("jam") } returns ongoing
            every { jamRepository.findById("jam") } returns Optional.of(ongoing)
            // Another upload by the same user committed while this build was processed.
            every { gameRepository.countByJamIdAndOwnerId("jam", owner.id) } returns 1

            shouldThrow<GmmException> {
                service.create("new", owner, "jam", details, GameBuild("new/b", "B", "b.zip"))
            }.code shouldBe ErrorCode.GAME_LIMIT_REACHED
            verifyOrder {
                jamRepository.findForUpdate("jam")
                gameRepository.countByJamIdAndOwnerId("jam", owner.id)
            }
            verify(exactly = 0) { gameRepository.save(any()) }
        }

        test("rejects a missing jam with 400 so the upload form stays") {
            every { jamRepository.findById("missing") } returns Optional.empty()

            shouldThrow<GmmException> { service.checkCanUpload(owner, "missing") }.code shouldBe
                ErrorCode.INVALID_GAME_INPUT
        }

        fun ongoing() = every { jamRepository.findById("jam") } returns
            Optional.of(jam("2026-11-18T00:00:00Z", "2026-11-20T00:00:00Z"))

        fun closed() = every { jamRepository.findById("jam") } returns
            Optional.of(jam("2026-11-10T00:00:00Z", "2026-11-12T00:00:00Z"))

        test("hides a game of an ongoing jam from other users; owner and admin may edit it") {
            every { gameRepository.findById("game") } returns Optional.of(game())
            ongoing()

            shouldThrow<GmmException> { service.checkCanUpdate(other, "game") }.code shouldBe ErrorCode.GAME_NOT_FOUND
            service.checkCanUpdate(owner, "game")
            service.checkCanUpdate(admin, "game")
        }

        test("an update replaces the details and the build") {
            val game = game()
            every { gameRepository.findById("game") } returns Optional.of(game)
            ongoing()

            val updated = service.update(
                owner,
                "game",
                details.copy(title = "새 제목"),
                GameBuild("g/new", "New", "new.zip"),
            )

            updated.title shouldBe "새 제목"
            updated.buildPath shouldBe "g/new"
            // No transaction is active in this unit test, so the old folder is deleted right away.
            verify { buildStorage.delete("g/old") }
        }

        test("after submissions close, nobody edits the details or the build, admins included") {
            val game = game()
            every { gameRepository.findById("game") } returns Optional.of(game)
            closed()
            val build = GameBuild("g/new", "New", "new.zip")

            listOf(owner, admin).forEach { user ->
                shouldThrow<GmmException> { service.checkCanUpdate(user, "game") }.message shouldBe
                    "제출이 마감되어 수정할 수 없어요."
                shouldThrow<GmmException> { service.update(user, "game", details, build) }.code shouldBe
                    ErrorCode.GAME_EDIT_CLOSED
                shouldThrow<GmmException> { service.update(user, "game", details.copy(title = "새 제목"), null) }
                    .code shouldBe ErrorCode.GAME_EDIT_CLOSED
            }
            game.buildPath shouldBe "g/old"
            game.title shouldBe "제목"
        }

        test(
            "games carry an optimistic lock, so overlapping edits and build swaps cannot overwrite each other",
        ) {
            Game::class.java.getDeclaredField("version").isAnnotationPresent(Version::class.java) shouldBe true
            // Null until persisted, so Spring Data persists a new game instead of merging it.
            game().version shouldBe null
        }

        test("an update without a build keeps the build") {
            every { gameRepository.findById("game") } returns Optional.of(game())
            ongoing()

            service.update(owner, "game", details, null).buildPath shouldBe "g/old"
        }

        test("owner and admin delete a game and its build even after submissions close; others get 404 or 403") {
            val game = game()
            every { gameRepository.findById("game") } returns Optional.of(game)
            every { gameRepository.delete(game) } just runs
            ongoing()

            shouldThrow<GmmException> { service.delete(other, "game") }.code shouldBe ErrorCode.GAME_NOT_FOUND
            closed()
            shouldThrow<GmmException> { service.delete(other, "game") }.code shouldBe ErrorCode.GAME_DELETE_NOT_OWNER

            service.delete(owner, "game")
            service.delete(admin, "game")

            verify(exactly = 2) { gameRepository.delete(game) }
            verify { buildStorage.deleteGame("game") }
        }
    })
