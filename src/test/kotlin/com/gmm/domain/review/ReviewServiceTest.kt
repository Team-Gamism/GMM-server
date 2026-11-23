package com.gmm.domain.review

import com.gmm.domain.game.entity.Game
import com.gmm.domain.game.entity.GameBuild
import com.gmm.domain.game.entity.GameDetails
import com.gmm.domain.game.entity.Genre
import com.gmm.domain.game.entity.Orientation
import com.gmm.domain.game.entity.Platform
import com.gmm.domain.game.repository.GameRepository
import com.gmm.domain.jam.entity.Jam
import com.gmm.domain.jam.repository.JamRepository
import com.gmm.domain.review.entity.Review
import com.gmm.domain.review.entity.ReviewScores
import com.gmm.domain.review.presentation.ReviewRequest
import com.gmm.domain.review.repository.ReviewRepository
import com.gmm.domain.review.service.ReviewService
import com.gmm.domain.user.entity.User
import com.gmm.domain.user.entity.UserRole
import com.gmm.domain.user.repository.UserRepository
import com.gmm.global.auth.AuthUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class ReviewServiceTest :
    FunSpec({
        val now = Instant.parse("2026-11-19T00:00:00Z")
        val reviewRepository = mockk<ReviewRepository>()
        val gameRepository = mockk<GameRepository>()
        val jamRepository = mockk<JamRepository>()
        val userRepository = mockk<UserRepository>()
        val service =
            ReviewService(
                reviewRepository,
                gameRepository,
                jamRepository,
                userRepository,
                Clock.fixed(now, ZoneOffset.UTC),
            )

        val owner = AuthUser("owner", UserRole.USER)
        val reviewer = AuthUser("reviewer", UserRole.USER)
        val admin = AuthUser("admin", UserRole.ADMIN)
        val valid = ReviewRequest(ReviewRequest.ScoresRequest(4, 5, 3, 4), "  아이디어가 좋아요!  ")
        lateinit var game: Game

        fun jam(start: String, end: String) =
            Jam("jam", "잼", "", "주제", null, Instant.parse(start), Instant.parse(end), "admin", now)

        beforeEach {
            clearAllMocks()
            val details =
                GameDetails(
                    "게임", "", "", Genre.ACTION, null, Orientation.LANDSCAPE,
                    listOf(
                        Platform.PC,
                    ),
                    emptyList(), "",
                )
            game = Game("game", "jam", owner.id, details, GameBuild("g/b", "B", "b.zip"), now).also { it.approve() }
            every { gameRepository.findById("game") } returns Optional.of(game)
            every { jamRepository.findById("jam") } returns
                Optional.of(jam("2026-11-18T00:00:00Z", "2026-11-20T00:00:00Z"))
            every { reviewRepository.findByGameIdAndAuthorId(any(), any()) } returns null
            every { reviewRepository.saveAndFlush(any()) } answers { firstArg() }
        }

        test("creates a review with trimmed comment") {
            val saved = slot<Review>()
            every { reviewRepository.saveAndFlush(capture(saved)) } answers { saved.captured }

            val response = service.upsertMine(reviewer, "game", valid)

            response.authorId shouldBe reviewer.id
            response.scores.theme shouldBe 5
            response.comment shouldBe "아이디어가 좋아요!"
            response.createdAt shouldBe now
        }

        test("updates the existing review instead of creating a second one") {
            val existing = Review("r1", "game", reviewer.id, ReviewScores(1, 1, 1, 1), "처음 평가예요", now.minusSeconds(60))
            every { reviewRepository.findByGameIdAndAuthorId("game", reviewer.id) } returns existing

            val response = service.upsertMine(reviewer, "game", valid)

            response.id shouldBe "r1"
            existing.scores shouldBe ReviewScores(4, 5, 3, 4)
            existing.updatedAt shouldBe now
            verify(exactly = 0) { reviewRepository.saveAndFlush(any()) }
        }

        test("rejects reviewing my own game, admins included") {
            shouldThrow<GmmException> { service.upsertMine(owner, "game", valid) }.code shouldBe
                ErrorCode.REVIEW_OWN_GAME
        }

        test("allows reviews only while the jam is ongoing or judging") {
            every { jamRepository.findById("jam") } returns
                Optional.of(jam("2026-11-10T00:00:00Z", "2026-11-12T00:00:00Z"))
            service.upsertMine(admin, "game", valid) // judging

            every { jamRepository.findById("jam") } returns
                Optional.of(jam("2026-11-25T00:00:00Z", "2026-11-26T00:00:00Z"))
            shouldThrow<GmmException> { service.upsertMine(admin, "game", valid) }.code shouldBe
                ErrorCode.REVIEW_NOT_OPEN
        }

        test("requires every score from 1 to 5 and a 5 to 1000 character comment") {
            listOf(
                valid.copy(scores = ReviewRequest.ScoresRequest(0, 5, 3, 4)),
                valid.copy(scores = ReviewRequest.ScoresRequest(4, 6, 3, 4)),
                valid.copy(scores = ReviewRequest.ScoresRequest(4, 5, null, 4)),
                valid.copy(scores = null),
            ).forEach {
                shouldThrow<GmmException> { service.upsertMine(reviewer, "game", it) }.message shouldBe
                    "모든 항목에 별점을 매겨주세요."
            }
            shouldThrow<GmmException> { service.upsertMine(reviewer, "game", valid.copy(comment = " 좋아요 ")) }
                .message shouldContain "5자 이상"
            shouldThrow<GmmException> { service.upsertMine(reviewer, "game", valid.copy(comment = "가".repeat(1001))) }
                .message shouldContain "1000자 이하"
        }

        test("hides unapproved games from other users with 404") {
            game.markPending()

            shouldThrow<GmmException> { service.upsertMine(reviewer, "game", valid) }.code shouldBe
                ErrorCode.GAME_NOT_FOUND
            shouldThrow<GmmException> { service.list(reviewer, "game") }.code shouldBe ErrorCode.GAME_NOT_FOUND
        }

        test("lists reviews with author names, falling back to 알 수 없음") {
            every { reviewRepository.findAllByGameIdOrderByCreatedAtDesc("game") } returns listOf(
                Review("r2", "game", "gone", ReviewScores(2, 2, 2, 2), "탈퇴한 사람", now),
                Review("r1", "game", reviewer.id, ReviewScores(5, 5, 5, 5), "최고예요!", now.minusSeconds(60)),
            )
            every { userRepository.findAllById(any()) } returns
                listOf(User(reviewer.id, 1, "r@gsm.hs.kr", "평가자", null, UserRole.USER, now))

            val reviews = service.list(null, "game")

            reviews.map { it.authorName } shouldBe listOf("알 수 없음", "평가자")
            reviews[1].scores.`fun` shouldBe 5
        }

        test("only the author or an admin deletes a review; a missing review is a no-op") {
            val review = Review("r1", "game", reviewer.id, ReviewScores(5, 5, 5, 5), "최고예요!", now)
            every { reviewRepository.findById("r1") } returns Optional.of(review)
            every { reviewRepository.findById("missing") } returns Optional.empty()
            every { reviewRepository.delete(review) } just runs

            shouldThrow<GmmException> { service.delete(owner, "r1") }.code shouldBe ErrorCode.REVIEW_NOT_AUTHOR
            service.delete(reviewer, "r1")
            service.delete(admin, "r1")
            service.delete(owner, "missing")

            verify(exactly = 2) { reviewRepository.delete(review) }
        }
    })
