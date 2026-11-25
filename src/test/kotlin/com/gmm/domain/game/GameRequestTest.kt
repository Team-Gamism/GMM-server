package com.gmm.domain.game

import com.gmm.domain.game.entity.Genre
import com.gmm.domain.game.entity.InputDevice
import com.gmm.domain.game.entity.Orientation
import com.gmm.domain.game.entity.Platform
import com.gmm.domain.game.presentation.GameRequest
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class GameRequestTest :
    FunSpec({
        val uploaded: (String) -> Boolean = { it.startsWith("https://games.test/images/") }
        val valid =
            GameRequest(
                jamId = "j-now",
                title = "  미니미 히어로  ",
                tagline = "줄어들수록 빨라지는 히어로의 탈출기",
                description = "조작법\n- 이동: 방향키",
                genre = Genre.ACTION,
                iconUrl = "https://games.test/images/abc.jpg",
                orientation = Orientation.LANDSCAPE,
                env =
                GameRequest.EnvRequest(
                    platforms = listOf(Platform.PC, Platform.PC),
                    inputs = listOf(InputDevice.KEYBOARD, InputDevice.MOUSE),
                    note = "크롬 최신 버전 권장",
                ),
            )

        fun rejected(request: GameRequest): GmmException =
            shouldThrow<GmmException> { request.toDetails(uploaded) }.also {
                it.code shouldBe
                    ErrorCode.INVALID_GAME_INPUT
            }

        test("trims the title and removes duplicate platforms") {
            val details = valid.toDetails(uploaded)

            details.title shouldBe "미니미 히어로"
            details.platforms shouldBe listOf(Platform.PC)
        }

        test("requires a title of at most 40 characters") {
            rejected(valid.copy(title = " ")).message shouldContain "제목을 입력"
            rejected(valid.copy(title = "가".repeat(41))).message shouldContain "40자 이하"
            valid.copy(title = "가".repeat(40)).toDetails(uploaded).title.length shouldBe 40
        }

        test("limits the tagline to 80 characters") {
            rejected(valid.copy(tagline = "가".repeat(81))).message shouldContain "80자 이하"
        }

        test("requires genre, orientation, and at least one platform") {
            rejected(valid.copy(genre = null)).message shouldContain "장르"
            rejected(valid.copy(orientation = null)).message shouldContain "화면 방향"
            rejected(valid.copy(env = valid.env!!.copy(platforms = emptyList()))).message shouldContain "플랫폼"
            rejected(valid.copy(env = null)).message shouldContain "플랫폼"
        }

        test("accepts only uploaded icon URLs and treats blank as no icon") {
            valid.toDetails(uploaded).iconUrl shouldBe "https://games.test/images/abc.jpg"
            valid.copy(iconUrl = " ").toDetails(uploaded).iconUrl shouldBe null
            rejected(valid.copy(iconUrl = "https://attacker.example/t.png")).message shouldContain "아이콘"
            rejected(valid.copy(iconUrl = "data:image/png;base64,AAAA")).message shouldContain "아이콘"
            rejected(valid.copy(iconUrl = "javascript:alert(1)")).message shouldContain "아이콘"
        }

        test("requires a jam on create") {
            valid.requireJamId() shouldBe "j-now"
            shouldThrow<GmmException> { valid.copy(jamId = "").requireJamId() }.message shouldContain "게임잼"
        }

        test("maps Korean API labels to enums") {
            Genre.fromLabel("플랫포머") shouldBe Genre.PLATFORMER
            Platform.fromLabel("모바일") shouldBe Platform.MOBILE
            InputDevice.fromLabel("게임패드") shouldBe InputDevice.GAMEPAD
            shouldThrow<IllegalArgumentException> { Genre.fromLabel("Action") }
        }
    })
