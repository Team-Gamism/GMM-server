package com.gmm.domain.game

import com.gmm.domain.build.storage.BuildStorage
import com.gmm.domain.game.entity.Game
import com.gmm.domain.game.entity.GameBuild
import com.gmm.domain.game.entity.GameDetails
import com.gmm.domain.game.entity.Genre
import com.gmm.domain.game.entity.Orientation
import com.gmm.domain.game.entity.Platform
import com.gmm.domain.game.entity.ReviewStatus
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
            Jam("jam", "잼", "", "주제", null, Instant.parse(start), Instant.parse(end), "admin", now)

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

        test("rejects a missing jam with 400 so the upload form stays") {
            every { jamRepository.findById("missing") } returns Optional.empty()

            shouldThrow<GmmException> { service.checkCanUpload(owner, "missing") }.code shouldBe
                ErrorCode.INVALID_GAME_INPUT
        }

        test("hides a pending game from other users and forbids editing an approved one") {
            val pending = game()
            every { gameRepository.findById("game") } returns Optional.of(pending)

            shouldThrow<GmmException> { service.checkCanUpdate(other, "game") }.code shouldBe ErrorCode.GAME_NOT_FOUND
            service.checkCanUpdate(owner, "game")
            service.checkCanUpdate(admin, "game")
        }

        test("a new build resets review to pending and clears the reject reason") {
            val game = game()
            every { gameRepository.findById("game") } returns Optional.of(game)

            val updated = service.update(
                owner,
                "game",
                details.copy(title = "새 제목"),
                GameBuild("g/new", "New", "new.zip"),
            )

            updated.title shouldBe "새 제목"
            updated.buildPath shouldBe "g/new"
            updated.reviewStatus shouldBe ReviewStatus.PENDING
            updated.rejectReason shouldBe null
            // No transaction is active in this unit test, so the old folder is deleted right away.
            verify { buildStorage.delete("g/old") }
        }

        test("an update without a build keeps the build") {
            val game = game()
            every { gameRepository.findById("game") } returns Optional.of(game)

            service.update(owner, "game", details, null).buildPath shouldBe "g/old"
        }

        test("owner and admin delete a game and its build folder; others get 403 or 404") {
            val game = game()
            every { gameRepository.findById("game") } returns Optional.of(game)
            every { gameRepository.delete(game) } just runs

            shouldThrow<GmmException> { service.delete(other, "game") }.code shouldBe ErrorCode.GAME_NOT_FOUND
            game.approve()
            shouldThrow<GmmException> { service.delete(other, "game") }.code shouldBe ErrorCode.GAME_DELETE_NOT_OWNER

            service.delete(owner, "game")
            service.delete(admin, "game")

            verify(exactly = 2) { gameRepository.delete(game) }
            verify { buildStorage.deleteGame("game") }
        }

        test("only admins set the upload status") {
            shouldThrow<GmmException> {
                service.setUploadStatus(owner, "game", ReviewStatus.APPROVED, null)
            }.code shouldBe
                ErrorCode.ADMIN_ONLY
        }

        test("rejecting keeps the trimmed reason or 사유 없음; other statuses clear it") {
            val game = game()
            every { gameRepository.findById("game") } returns Optional.of(game)

            service.setUploadStatus(admin, "game", ReviewStatus.REJECTED, "  빌드가 실행되지 않아요.  ")
            game.reviewStatus shouldBe ReviewStatus.REJECTED
            game.rejectReason shouldBe "빌드가 실행되지 않아요."
            game.updatedAt shouldBe now

            service.setUploadStatus(admin, "game", ReviewStatus.REJECTED, " ").rejectReason shouldBe "사유 없음"
            service.setUploadStatus(admin, "game", ReviewStatus.APPROVED, "무시돼요").rejectReason shouldBe null
            game.reviewStatus shouldBe ReviewStatus.APPROVED
            service.setUploadStatus(admin, "game", ReviewStatus.PENDING, null).reviewStatus shouldBe
                ReviewStatus.PENDING
        }

        test("rejects reasons longer than 500 characters") {
            every { gameRepository.findById("game") } returns Optional.of(game())

            shouldThrow<GmmException> {
                service.setUploadStatus(admin, "game", ReviewStatus.REJECTED, "가".repeat(501))
            }.code shouldBe ErrorCode.INVALID_UPLOAD_STATUS
        }
    })
