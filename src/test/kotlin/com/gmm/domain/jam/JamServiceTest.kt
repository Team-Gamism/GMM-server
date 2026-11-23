package com.gmm.domain.jam

import com.gmm.domain.build.storage.BuildStorage
import com.gmm.domain.game.repository.GameRepository
import com.gmm.domain.jam.entity.Jam
import com.gmm.domain.jam.entity.JamAward
import com.gmm.domain.jam.entity.JamDetails
import com.gmm.domain.jam.repository.JamAwardRepository
import com.gmm.domain.jam.repository.JamRepository
import com.gmm.domain.jam.service.JamService
import com.gmm.domain.user.entity.UserRole
import com.gmm.global.auth.AuthUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class JamServiceTest :
    FunSpec({
        val now = Instant.parse("2026-11-19T00:00:00Z")
        val jamRepository = mockk<JamRepository>(relaxed = true)
        val awardRepository = mockk<JamAwardRepository>()
        val gameRepository = mockk<GameRepository>()
        val buildStorage = mockk<BuildStorage>(relaxed = true)
        val service =
            JamService(jamRepository, awardRepository, gameRepository, buildStorage, Clock.fixed(now, ZoneOffset.UTC))

        val admin = AuthUser("admin", UserRole.ADMIN)
        val user = AuthUser("user", UserRole.USER)
        val details =
            JamDetails(
                "잼",
                "",
                "주제",
                null,
                Instant.parse("2026-11-18T00:00:00Z"),
                Instant.parse("2026-11-20T00:00:00Z"),
            )

        fun jam(id: String = "jam") =
            Jam(id, "잼", "", "주제", null, details.startAt, details.endAt, admin.id, now.minusSeconds(60))

        beforeEach {
            clearAllMocks()
            every { awardRepository.findAllByJamIdIn(any()) } returns emptyList()
        }

        test("only admins create, update, and delete jams") {
            listOf<() -> Unit>(
                { service.create(user, details) },
                { service.update(user, "jam", details) },
                { service.delete(user, "jam") },
            ).forEach { shouldThrow<GmmException> { it() }.code shouldBe ErrorCode.ADMIN_ONLY }
            verify(exactly = 0) { jamRepository.save(any()) }
            verify(exactly = 0) { jamRepository.delete(any()) }
        }

        test("create sets the host and starts with no announcement or awards") {
            val saved = slot<Jam>()
            every { jamRepository.save(capture(saved)) } answers { saved.captured }

            val response = service.create(admin, details)

            response.hostId shouldBe admin.id
            response.createdAt shouldBe now
            response.announcedAt shouldBe null
            response.awards shouldBe emptyList()
        }

        test("returns 404 for a missing jam") {
            every { jamRepository.findById("missing") } returns Optional.empty()

            shouldThrow<GmmException> { service.get("missing") }.code shouldBe ErrorCode.JAM_NOT_FOUND
            shouldThrow<GmmException> { service.update(admin, "missing", details) }.code shouldBe
                ErrorCode.JAM_NOT_FOUND
        }

        test("update changes the fields and the update time") {
            val jam = jam()
            every { jamRepository.findById("jam") } returns Optional.of(jam)

            val response = service.update(admin, "jam", details.copy(name = "새 이름", coverUrl = "https://c/x.jpg"))

            response.name shouldBe "새 이름"
            response.coverUrl shouldBe "https://c/x.jpg"
            jam.updatedAt shouldBe now
        }

        test("delete removes the jam and the build folders of its games") {
            val jam = jam()
            every { jamRepository.findById("jam") } returns Optional.of(jam)
            every { gameRepository.findIdsByJamId("jam") } returns listOf("g1", "g2")

            service.delete(admin, "jam")

            verify { jamRepository.delete(jam) }
            // No transaction is active in this unit test, so the folders are deleted right away.
            verify { buildStorage.deleteGame("g1") }
            verify { buildStorage.deleteGame("g2") }
        }

        test("list keeps the repository order and attaches awards sorted by rank") {
            every { jamRepository.findAllByOrderByStartAtDesc() } returns listOf(jam("new"), jam("old"))
            every { awardRepository.findAllByJamIdIn(listOf("new", "old")) } returns
                listOf(JamAward("old", "g2", 2, "최우수상"), JamAward("old", "g1", 1, "대상"))

            val jams = service.list()

            jams.map { it.id } shouldBe listOf("new", "old")
            jams[0].awards shouldBe emptyList()
            jams[1].awards.map { it.title } shouldBe listOf("대상", "최우수상")
        }
    })
