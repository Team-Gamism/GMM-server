package com.gmm.domain.game

import com.gmm.domain.build.storage.BuildStorage
import com.gmm.domain.game.entity.Game
import com.gmm.domain.game.entity.GameBuild
import com.gmm.domain.game.entity.GameDetails
import com.gmm.domain.game.entity.Genre
import com.gmm.domain.game.entity.Orientation
import com.gmm.domain.game.entity.Platform
import com.gmm.domain.game.repository.GameRepository
import com.gmm.domain.game.service.GameQuery
import com.gmm.domain.game.service.GameQueryService
import com.gmm.domain.game.service.GameSort
import com.gmm.domain.game.service.GameSummaries
import com.gmm.domain.jam.entity.Jam
import com.gmm.domain.jam.repository.JamRepository
import com.gmm.domain.review.repository.ReviewRepository
import com.gmm.domain.review.repository.ReviewStats
import com.gmm.domain.user.entity.User
import com.gmm.domain.user.entity.UserRole
import com.gmm.domain.user.repository.UserRepository
import com.gmm.global.auth.AuthUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

private const val OPEN = "open"

class GameQueryServiceTest :
    FunSpec({
        val base = Instant.parse("2026-11-19T00:00:00Z")
        val gameRepository = mockk<GameRepository>()
        val userRepository = mockk<UserRepository>()
        val jamRepository = mockk<JamRepository>()
        val reviewRepository = mockk<ReviewRepository>()
        val buildStorage = mockk<BuildStorage> {
            every { publicUrl(any()) } answers
                { "https://g/${firstArg<String>()}" }
        }
        val service =
            GameQueryService(
                gameRepository,
                jamRepository,
                GameSummaries(userRepository, jamRepository, reviewRepository, buildStorage),
                Clock.fixed(base, ZoneOffset.UTC),
            )

        val owner = AuthUser("owner", UserRole.USER)
        val other = AuthUser("other", UserRole.USER)
        val admin = AuthUser("admin", UserRole.ADMIN)

        fun game(
            id: String,
            minute: Long,
            ownerId: String = owner.id,
            jamId: String = "jam",
            genre: Genre = Genre.ACTION,
            orientation: Orientation = Orientation.LANDSCAPE,
            title: String = "게임 $id",
        ): Game {
            val details = GameDetails(title, "", "", genre, null, orientation, listOf(Platform.PC), emptyList(), "")
            return Game(id, jamId, ownerId, details, GameBuild("$id/b", "B", "b.zip"), base.plusSeconds(minute * 60))
        }

        fun stats(id: String, average: Double?) = ReviewStats(
            id,
            if (average ==
                null
            ) {
                0
            } else {
                2
            },
            average,
            null,
            null,
            null,
            null,
        )

        fun setUp(games: List<Game>, stats: List<ReviewStats> = emptyList()) {
            // Stands in for the query: a null argument matches all.
            every { gameRepository.findAllByJamAndOwner(any(), any()) } answers {
                val jamId = firstArg<String?>()
                val ownerId = secondArg<String?>()
                games.filter { (jamId == null || it.jamId == jamId) && (ownerId == null || it.ownerId == ownerId) }
            }
            every { reviewRepository.findStats(any()) } returns stats
            every { userRepository.findAllById(any()) } returns
                listOf(User("owner", 1, "o@gsm.hs.kr", "김하늘", "1101", UserRole.USER, base))
            // "open" still takes submissions; every other jam has closed them.
            every { jamRepository.findAllById(any()) } answers {
                firstArg<Iterable<String>>().map { id ->
                    val start = if (id == OPEN) base.minusSeconds(60) else base.minusSeconds(7200)
                    Jam(id, "제4회 게임잼", "", "주제", null, start, start.plusSeconds(3600), 7, "admin", base)
                }
            }
            every { jamRepository.findById(any()) } answers {
                Optional.of(jamRepository.findAllById(listOf(firstArg<String>())).single())
            }
        }

        fun ids(user: AuthUser?, query: GameQuery = GameQuery()) = service.list(user, query).map { it.game.id }

        test("shows only games of closed jams by default, even with includeHidden for guests") {
            setUp(listOf(game("a", 1), game("p", 2, jamId = OPEN)))

            ids(null) shouldBe listOf("a")
            ids(null, GameQuery(includeHidden = true)) shouldBe listOf("a")
            ids(owner) shouldBe listOf("a")
        }

        test("mixes in hidden games the caller may see when ownerId or includeHidden is given") {
            setUp(
                listOf(
                    game("a", 1),
                    game("mine", 2, jamId = OPEN),
                    game("theirs", 3, jamId = OPEN, ownerId = "x"),
                ),
            )

            ids(owner, GameQuery(ownerId = owner.id)) shouldBe listOf("mine", "a")
            ids(owner, GameQuery(includeHidden = true)) shouldBe listOf("mine", "a")
            ids(other, GameQuery(includeHidden = true)) shouldBe listOf("a")
            ids(admin, GameQuery(includeHidden = true)) shouldBe listOf("theirs", "mine", "a")
        }

        test("filters by jam, genre, and orientation") {
            setUp(
                listOf(
                    game("a", 1),
                    game("b", 2, jamId = "other"),
                    game("c", 3, genre = Genre.PUZZLE),
                    game("d", 4, orientation = Orientation.PORTRAIT),
                ),
            )

            ids(null, GameQuery(jamId = "jam")) shouldBe listOf("d", "c", "a")
            verify { gameRepository.findAllByJamAndOwner("jam", null) }
            ids(null, GameQuery(genre = Genre.PUZZLE)) shouldBe listOf("c")
            ids(null, GameQuery(orientation = Orientation.PORTRAIT)) shouldBe listOf("d")
        }

        test("searches title, tagline, owner name, and genre label, ignoring case") {
            setUp(listOf(game("a", 1, title = "Mini Hero"), game("b", 2, genre = Genre.PUZZLE, title = "다른 게임")))

            ids(null, GameQuery(q = "mini")) shouldBe listOf("a")
            ids(null, GameQuery(q = "하늘")) shouldBe listOf("b", "a")
            ids(null, GameQuery(q = "퍼즐")) shouldBe listOf("b")
        }

        test("rating sort puts unrated games last, then newest first") {
            setUp(
                listOf(game("low", 1), game("none", 2), game("high", 3), game("tie", 4)),
                listOf(stats("low", 2.5), stats("high", 4.25), stats("tie", 4.25)),
            )

            ids(null, GameQuery(sort = GameSort.RATING)) shouldBe listOf("tie", "high", "low", "none")
            ids(null) shouldBe listOf("tie", "high", "none", "low")
        }

        test("summary carries names, review count, and average") {
            setUp(listOf(game("a", 1)), listOf(stats("a", 2.63)))

            val summary = service.list(null, GameQuery()).single()

            summary.ownerName shouldBe "김하늘"
            summary.jamName shouldBe "제4회 게임잼"
            summary.reviewCount shouldBe 2
            summary.average shouldBe 2.63
            summary.game.buildUrl shouldBe "https://g/a/b"
        }

        test("detail hides games of a jam still taking submissions from others with 404") {
            val hidden = game("p", 1, jamId = OPEN)
            every { gameRepository.findById("p") } returns Optional.of(hidden)
            setUp(listOf(hidden))

            shouldThrow<GmmException> { service.get(null, "p") }.code shouldBe ErrorCode.GAME_NOT_FOUND
            shouldThrow<GmmException> { service.get(other, "p") }.code shouldBe ErrorCode.GAME_NOT_FOUND
            service.get(owner, "p").reviewCount shouldBe 0
            service.get(admin, "p").average shouldBe null
        }
    })
