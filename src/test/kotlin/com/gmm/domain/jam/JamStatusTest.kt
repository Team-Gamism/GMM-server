package com.gmm.domain.jam

import com.gmm.domain.jam.entity.JamStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class JamStatusTest :
    FunSpec({
        val start = Instant.parse("2026-11-18T09:00:00Z")
        val end = Instant.parse("2026-11-20T09:00:00Z")

        fun at(now: String) = JamStatus.of(start, end, 7, null, Instant.parse(now))

        test("follows the time window when not announced") {
            at("2026-11-18T08:59:59Z") shouldBe JamStatus.UPCOMING
            at("2026-11-18T09:00:00Z") shouldBe JamStatus.ONGOING
            at("2026-11-20T09:00:00Z") shouldBe JamStatus.ONGOING
            at("2026-11-20T09:00:01Z") shouldBe JamStatus.REVIEWING
            at("2026-11-27T09:00:00Z") shouldBe JamStatus.REVIEWING
            at("2026-11-27T09:00:01Z") shouldBe JamStatus.JUDGING
        }

        test("announcement wins over the time window") {
            JamStatus.of(start, end, 7, end, Instant.parse("2026-11-19T00:00:00Z")) shouldBe JamStatus.ANNOUNCED
        }

        test("uploads only while ongoing, reviews only while reviewing, games public once submissions close") {
            JamStatus.entries.filter { it.acceptsUploads } shouldBe listOf(JamStatus.ONGOING)
            JamStatus.entries.filter { it.acceptsReviews } shouldBe listOf(JamStatus.REVIEWING)
            JamStatus.entries.filter { it.isPublic } shouldBe
                listOf(JamStatus.REVIEWING, JamStatus.JUDGING, JamStatus.ANNOUNCED)
        }
    })
