package com.gmm.domain.game

import com.gmm.domain.game.service.GameUploadService.Companion.displayFileName
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GameUploadServiceTest :
    FunSpec({
        fun length(name: String) = name.codePointCount(0, name.length)

        test("keeps the base name of a client path and drops control characters") {
            displayFileName("C:\\Users\\me\\My\u0000Game.ZIP") shouldBe "MyGame.ZIP"
            displayFileName("/tmp/게임.zip") shouldBe "게임.zip"
            displayFileName(null) shouldBe "build.zip"
            displayFileName(" ") shouldBe "build.zip"
        }

        test("checks .zip on the full name, before shortening") {
            val long = "a".repeat(300) + ".zip"

            val shortened = displayFileName(long)

            shortened shouldBe "a".repeat(251) + ".zip"
            length(shortened) shouldBe 255
            displayFileName("a".repeat(251) + ".zip") shouldBe "a".repeat(251) + ".zip"
        }

        test("rejects names that do not end in .zip, however long") {
            shouldThrow<GmmException> { displayFileName("build.rar") }.code shouldBe ErrorCode.BUILD_INVALID
            shouldThrow<GmmException> { displayFileName("a".repeat(251) + ".zip.exe") }.code shouldBe
                ErrorCode.BUILD_INVALID
        }

        test("shortens Korean and emoji names by character without splitting a surrogate pair") {
            val shortenedKorean = displayFileName("게".repeat(300) + ".zip")
            shortenedKorean shouldBe "게".repeat(251) + ".zip"

            val shortenedEmoji = displayFileName("\uD83C\uDFAE".repeat(300) + ".Zip")
            length(shortenedEmoji) shouldBe 255
            shortenedEmoji shouldBe "\uD83C\uDFAE".repeat(251) + ".Zip"
        }
    })
