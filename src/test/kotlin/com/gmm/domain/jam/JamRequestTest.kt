package com.gmm.domain.jam

import com.gmm.domain.jam.presentation.JamRequest
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Instant

class JamRequestTest :
    FunSpec({
        val valid =
            JamRequest(
                name = "  제5회 게이미즘 게임잼  ",
                description = "설명",
                theme = " 연결 ",
                coverUrl = "https://cdn.example/covers/5.jpg",
                startAt = Instant.parse("2026-11-18T09:00:00.123456Z"),
                endAt = Instant.parse("2026-11-20T09:00:00Z"),
            )

        fun rejected(request: JamRequest): GmmException =
            shouldThrow<GmmException> { request.toDetails() }.also { it.code shouldBe ErrorCode.INVALID_JAM_INPUT }

        test("trims text and truncates times to milliseconds") {
            val details = valid.toDetails()

            details.name shouldBe "제5회 게이미즘 게임잼"
            details.theme shouldBe "연결"
            details.startAt shouldBe Instant.parse("2026-11-18T09:00:00.123Z")
        }

        test("requires name and theme") {
            rejected(valid.copy(name = " ")).message shouldContain "이름"
            rejected(valid.copy(theme = null)).message shouldContain "주제"
            rejected(valid.copy(name = "가".repeat(101))).message shouldContain "100자 이하"
        }

        test("requires a period whose end is after its start") {
            rejected(valid.copy(startAt = null)).message shouldContain "시작 시각"
            rejected(valid.copy(endAt = null)).message shouldContain "종료 시각"
            rejected(valid.copy(endAt = valid.startAt)).message shouldContain "뒤여야"
        }

        test("accepts only http(s) cover URLs and treats blank as no cover") {
            valid.copy(coverUrl = "").toDetails().coverUrl shouldBe null
            rejected(valid.copy(coverUrl = "data:image/jpeg;base64,AAAA")).message shouldContain "대표 이미지"
        }
    })
