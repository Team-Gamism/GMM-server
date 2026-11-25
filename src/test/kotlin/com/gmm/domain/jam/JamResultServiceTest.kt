package com.gmm.domain.jam

import com.gmm.domain.game.entity.Game
import com.gmm.domain.game.entity.GameBuild
import com.gmm.domain.game.entity.GameDetails
import com.gmm.domain.game.entity.Genre
import com.gmm.domain.game.entity.Orientation
import com.gmm.domain.game.entity.Platform
import com.gmm.domain.game.entity.ReviewStatus
import com.gmm.domain.game.presentation.GameResponse
import com.gmm.domain.game.presentation.GameSummaryResponse
import com.gmm.domain.game.repository.GameRepository
import com.gmm.domain.game.service.GameSummaries
import com.gmm.domain.jam.entity.Jam
import com.gmm.domain.jam.entity.JamAward
import com.gmm.domain.jam.presentation.AwardsRequest
import com.gmm.domain.jam.presentation.AwardsRequest.AwardRequest
import com.gmm.domain.jam.presentation.RankingEntryResponse.CriterionResponse
import com.gmm.domain.jam.repository.JamAwardRepository
import com.gmm.domain.jam.repository.JamRepository
import com.gmm.domain.jam.service.JamResultService
import com.gmm.domain.review.repository.ReviewStats
import com.gmm.domain.user.entity.UserRole
import com.gmm.global.auth.AuthUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class JamResultServiceTest :
    FunSpec({
        val now = Instant.parse("2026-11-21T00:00:00Z")
        val jamRepository = mockk<JamRepository>()
        val awardRepository = mockk<JamAwardRepository>(relaxed = true)
        val gameRepository = mockk<GameRepository>()
        val summaries = mockk<GameSummaries>()
        val service =
            JamResultService(
                jamRepository,
                awardRepository,
                gameRepository,
                summaries,
                Clock.fixed(now, ZoneOffset.UTC),
            )
        val admin = AuthUser("admin", UserRole.ADMIN)
        val user = AuthUser("user", UserRole.USER)
        lateinit var jam: Jam

        fun game(id: String) = Game(
            id,
            "jam",
            "owner",
            GameDetails(
                "게임 $id", "", "", Genre.ACTION, null, Orientation.LANDSCAPE,
                listOf(
                    Platform.PC,
                ),
                emptyList(), "",
            ),
            GameBuild("$id/b", "B", "b.zip"),
            now,
        ).also { it.approve() }

        fun summary(game: Game, average: Double?) =
            GameSummaryResponse(GameResponse.from(game) { it }, "제작자", "잼", if (average == null) 0 else 1, average)

        fun setJam(start: String, end: String) {
            jam = Jam("jam", "잼", "", "주제", null, Instant.parse(start), Instant.parse(end), "admin", now)
            every { jamRepository.findById("jam") } returns Optional.of(jam)
        }

        val games = listOf(game("a"), game("b"), game("c"))

        beforeEach {
            clearAllMocks()
            setJam("2026-11-18T00:00:00Z", "2026-11-20T00:00:00Z") // judging
            every {
                gameRepository.findAllByJamIdAndReviewStatusOrderByCreatedAtAsc("jam", ReviewStatus.APPROVED)
            } returns
                games
            every { awardRepository.saveAll(any<List<JamAward>>()) } answers { firstArg() }
        }

        test("ranking ranks overall and each criterion, sorted by overall rank") {
            val stats = mapOf(
                "a" to ReviewStats("a", 1, 3.0, 5.0, 1.0, 3.0, 3.0),
                "b" to ReviewStats("b", 1, 4.0, 4.0, 4.0, 4.0, 4.0),
            )
            every { summaries.stats(listOf("a", "b", "c")) } returns stats
            every { summaries.summarize(games, stats) } returns
                listOf(summary(games[0], 3.0), summary(games[1], 4.0), summary(games[2], null))

            val ranking = service.ranking(admin, "jam")

            ranking.map { it.game.game.id to it.rank } shouldBe listOf("b" to 1, "a" to 2, "c" to 3)
            ranking[1].criteria.getValue("fun") shouldBe CriterionResponse(5.0, 1)
            ranking[1].criteria.getValue("theme").rank shouldBe 2
            ranking[2].overall shouldBe null
            ranking[2].criteria.getValue("polish") shouldBe CriterionResponse(null, 3)
            ranking.first().criteria.keys.toList() shouldBe listOf("fun", "theme", "polish", "creativity")
        }

        test("announce replaces the awards and sets announcedAt") {
            val response = service.announce(
                admin,
                "jam",
                AwardsRequest(listOf(AwardRequest("b", 1, " 대상 "), AwardRequest("a", 2, "최우수상"))),
            )

            response.announcedAt shouldBe now
            response.awards.map { it.title } shouldBe listOf("대상", "최우수상")
            verifyOrder {
                awardRepository.deleteAllByJamId("jam")
                awardRepository.saveAll(any<List<JamAward>>())
            }
        }

        test("announce accepts an empty award list and works again after announcement") {
            service.announce(admin, "jam", AwardsRequest(emptyList())).awards shouldBe emptyList()
            service.announce(admin, "jam", AwardsRequest(emptyList())).announcedAt shouldBe now
        }

        test("announce is rejected before the jam ends and for non-admins") {
            shouldThrow<GmmException> { service.announce(user, "jam", AwardsRequest(emptyList())) }.code shouldBe
                ErrorCode.ADMIN_ONLY
            setJam("2026-11-20T00:00:00Z", "2026-11-22T00:00:00Z") // ongoing
            shouldThrow<GmmException> { service.announce(admin, "jam", AwardsRequest(emptyList())) }.code shouldBe
                ErrorCode.JAM_NOT_FINISHED
            setJam("2026-11-25T00:00:00Z", "2026-11-26T00:00:00Z") // upcoming
            shouldThrow<GmmException> { service.announce(admin, "jam", AwardsRequest(emptyList())) }.code shouldBe
                ErrorCode.JAM_NOT_FINISHED
            verify(exactly = 0) { awardRepository.deleteAllByJamId(any()) }
        }

        test("announce validates each award") {
            fun rejected(vararg awards: AwardRequest) =
                shouldThrow<GmmException> { service.announce(admin, "jam", AwardsRequest(awards.toList())) }.message

            rejected(AwardRequest("other-jam-game", 1, "대상")) shouldContain "확인 완료된 게임"
            rejected(AwardRequest("a", 1, "대상"), AwardRequest("a", 2, "우수상")) shouldContain "두 번"
            rejected(AwardRequest("a", 0, "대상")) shouldContain "순위"
            rejected(AwardRequest("a", 1, " ")) shouldContain "상 이름을 입력"
            rejected(AwardRequest("a", 1, "가".repeat(51))) shouldContain "50자 이하"
            shouldThrow<GmmException> { service.announce(admin, "jam", AwardsRequest(null)) }.code shouldBe
                ErrorCode.INVALID_AWARD
        }

        test("cancel clears announcedAt and keeps the awards") {
            jam.announce(now.minusSeconds(60))
            every { awardRepository.findAllByJamIdIn(listOf("jam")) } returns listOf(JamAward("jam", "a", 1, "대상"))

            val response = service.cancel(admin, "jam")

            response.announcedAt shouldBe null
            response.awards.single().title shouldBe "대상"
            verify(exactly = 0) { awardRepository.deleteAllByJamId(any()) }
        }

        test("ranking of a missing jam is 404") {
            every { jamRepository.findById("missing") } returns Optional.empty()

            shouldThrow<GmmException> { service.ranking(admin, "missing") }.code shouldBe ErrorCode.JAM_NOT_FOUND
        }

        test("before the results are announced only admins see the ranking") {
            every { summaries.stats(any()) } returns emptyMap()
            every { summaries.summarize(games, emptyMap()) } returns games.map { summary(it, null) }

            shouldThrow<GmmException> { service.ranking(user, "jam") }.code shouldBe ErrorCode.RESULTS_NOT_ANNOUNCED
            shouldThrow<GmmException> { service.ranking(null, "jam") }.code shouldBe ErrorCode.RESULTS_NOT_ANNOUNCED
            service.ranking(admin, "jam").size shouldBe 3

            jam.announce(now)
            service.ranking(user, "jam").size shouldBe 3
            service.ranking(null, "jam").size shouldBe 3
        }
    })
