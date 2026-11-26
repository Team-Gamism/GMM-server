package com.gmm.domain.user

import com.gmm.domain.game.entity.Game
import com.gmm.domain.game.entity.GameBuild
import com.gmm.domain.game.entity.GameDetails
import com.gmm.domain.game.entity.Genre
import com.gmm.domain.game.entity.Orientation
import com.gmm.domain.game.entity.Platform
import com.gmm.domain.game.repository.GameRepository
import com.gmm.domain.game.service.GameSummaries
import com.gmm.domain.jam.entity.Jam
import com.gmm.domain.jam.entity.JamAward
import com.gmm.domain.jam.repository.JamAwardRepository
import com.gmm.domain.jam.repository.JamRepository
import com.gmm.domain.user.entity.User
import com.gmm.domain.user.entity.UserRole
import com.gmm.domain.user.presentation.AvatarRequest
import com.gmm.domain.user.repository.UserRepository
import com.gmm.domain.user.service.ProfileService
import com.gmm.global.auth.AuthUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class ProfileServiceTest :
    FunSpec({
        val now = Instant.parse("2026-11-21T00:00:00Z")
        val userRepository = mockk<UserRepository>()
        val gameRepository = mockk<GameRepository>()
        val jamRepository = mockk<JamRepository>()
        val awardRepository = mockk<JamAwardRepository>()
        val summaries = mockk<GameSummaries>()
        val service = ProfileService(
            userRepository,
            gameRepository,
            jamRepository,
            awardRepository,
            summaries,
            Clock.fixed(now, ZoneOffset.UTC),
        )
        val user = User("u1", 1, "u1@gsm.hs.kr", "김하늘", "1101", UserRole.USER, now)

        fun game(id: String, jamId: String = "jam") = Game(
            id,
            jamId,
            user.id,
            GameDetails(
                "게임 $id", "", "", Genre.ACTION, null, Orientation.LANDSCAPE,
                listOf(
                    Platform.PC,
                ),
                emptyList(), "",
            ),
            GameBuild("$id/b", "B", "b.zip"),
            now,
        )

        fun jam(id: String, start: String, announced: Boolean): Jam {
            val startAt = Instant.parse(start)
            return Jam(id, "잼 $id", "", "주제", null, startAt, startAt.plusSeconds(3600), 7, "admin", now)
                .also { if (announced) it.announce(now) }
        }

        test("counts entries of closed jams and rank-1 awards of announced jams only") {
            val games = listOf(game("g1"), game("g2"))
            every { userRepository.findById("u1") } returns Optional.of(user)
            every { gameRepository.findAllByOwnerIdOrderByCreatedAtDesc("u1") } returns
                games + game("g3", jamId = "open")
            every { jamRepository.findAllById(setOf("jam", "open")) } returns listOf(
                jam("jam", "2026-11-01T00:00:00Z", announced = false),
                jam("open", "2026-11-20T23:00:00Z", announced = false), // still taking submissions
            )
            every { awardRepository.findAllByGameIdIn(setOf("g1", "g2")) } returns listOf(
                JamAward("old", "g1", 1, "대상"),
                JamAward("new", "g2", 2, "최우수상"),
                JamAward("new", "g1", 1, "대상"),
                JamAward("hidden", "g2", 1, "대상"),
            )
            every { jamRepository.findAllById(setOf("old", "new", "hidden")) } returns listOf(
                jam("old", "2026-09-01T00:00:00Z", announced = true),
                jam("new", "2026-11-01T00:00:00Z", announced = true),
                jam("hidden", "2026-11-10T00:00:00Z", announced = false),
            )
            every { summaries.summarize(games) } returns emptyList()

            val profile = service.profile("u1")

            profile.entryCount shouldBe 2
            profile.winCount shouldBe 2
            profile.awards.map { "${it.jamId}:${it.rank}:${it.gameTitle}" } shouldBe
                listOf("new:1:게임 g1", "new:2:게임 g2", "old:1:게임 g1")
            profile.awards.first().jamName shouldBe "잼 new"
        }

        test("a missing user is 404") {
            every { userRepository.findById("nope") } returns Optional.empty()

            shouldThrow<GmmException> { service.profile("nope") }.code shouldBe ErrorCode.USER_NOT_FOUND
        }

        test("changes and clears the avatar") {
            every { userRepository.findById("u1") } returns Optional.of(user)
            val me = AuthUser("u1", UserRole.USER)

            service.changeAvatar(me, "https://games.test/images/a.jpg").avatarUrl shouldBe
                "https://games.test/images/a.jpg"
            user.updatedAt shouldBe now
            service.changeAvatar(me, null).avatarUrl shouldBe null
        }

        test("avatar URL must come from the image upload API; blank means none") {
            val uploaded: (String) -> Boolean = { it.startsWith("https://games.test/images/") }
            AvatarRequest(" https://games.test/images/a.jpg ").normalized(uploaded) shouldBe
                "https://games.test/images/a.jpg"
            shouldThrow<GmmException> {
                AvatarRequest("https://attacker.example/t.png").normalized(uploaded)
            }.code shouldBe
                ErrorCode.INVALID_AVATAR
            AvatarRequest("  ").normalized(uploaded) shouldBe null
            AvatarRequest(null).normalized(uploaded) shouldBe null
            shouldThrow<GmmException> {
                AvatarRequest("data:image/jpeg;base64,AAAA").normalized(uploaded)
            }.code shouldBe
                ErrorCode.INVALID_AVATAR
        }
    })
