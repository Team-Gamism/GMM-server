package com.gmm.domain.health

import com.gmm.domain.health.presentation.HealthController
import com.gmm.domain.health.presentation.HealthResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.health.contributor.Status
import org.springframework.http.HttpStatus

class HealthControllerTest :
    FunSpec({
        fun controllerWith(status: Status): HealthController {
            val descriptor = mockk<HealthDescriptor> { every { this@mockk.status } returns status }
            val endpoint = mockk<HealthEndpoint> { every { health() } returns descriptor }
            return HealthController(endpoint)
        }

        test("UP when every health check passes") {
            val response = controllerWith(Status.UP).health()

            response.statusCode shouldBe HttpStatus.OK
            response.body shouldBe HealthResponse("UP")
        }

        test("503 DOWN when any check fails or is unknown") {
            listOf(Status.DOWN, Status.OUT_OF_SERVICE, Status.UNKNOWN).forEach { status ->
                val response = controllerWith(status).health()

                response.statusCode shouldBe HttpStatus.SERVICE_UNAVAILABLE
                response.body shouldBe HealthResponse("DOWN")
            }
        }
    })
