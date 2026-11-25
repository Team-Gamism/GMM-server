package com.gmm.global.exception

import com.gmm.domain.game.entity.Game
import com.gmm.global.config.GmmProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import org.springframework.orm.ObjectOptimisticLockingFailureException

class GlobalExceptionHandlerTest :
    FunSpec({
        test("a write that lost a race with another request is 409, not 500") {
            val response =
                GlobalExceptionHandler(GmmProperties())
                    .handleStaleWrite(ObjectOptimisticLockingFailureException(Game::class.java, "game"))

            response.statusCode shouldBe HttpStatus.CONFLICT
            response.body?.code shouldBe ErrorCode.CONFLICT.name
        }
    })
