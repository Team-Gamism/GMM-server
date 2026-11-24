package com.gmm.global.auth

import com.gmm.global.config.GmmProperties
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GlobalExceptionHandler
import com.gmm.global.exception.GmmException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

class OriginCheckInterceptorTest :
    FunSpec({
        val frontend = "https://gmm.https.gsmsv.site"
        val properties = GmmProperties(
            cors = GmmProperties.Cors(listOf("https://other-front.example", "")),
            frontend = GmmProperties.Frontend(frontend),
        )
        val interceptor = OriginCheckInterceptor(properties)

        fun allows(method: String, origin: String?): Boolean {
            val request = MockHttpServletRequest(method, "/api/v1/games")
            origin?.let { request.addHeader("Origin", it) }
            return interceptor.preHandle(request, MockHttpServletResponse(), Any())
        }

        test("write requests from the frontend or a CORS origin pass, ignoring case and a trailing slash") {
            allows("POST", frontend) shouldBe true
            allows("DELETE", "HTTPS://GMM.https.gsmsv.site/") shouldBe true
            allows("PUT", "https://other-front.example") shouldBe true
        }

        test("write requests from a build page, a sibling subdomain or an opaque origin are rejected") {
            listOf(
                "https://gmm-games.https.gsmsv.site",
                "https://someone.https.gsmsv.site",
                "http://gmm.https.gsmsv.site",
                "null",
            ).forEach { origin ->
                listOf("POST", "PUT", "PATCH", "DELETE").forEach { method ->
                    shouldThrow<GmmException> { allows(method, origin) }.code shouldBe ErrorCode.CROSS_SITE_REQUEST
                }
            }
        }

        test("safe methods and requests without Origin pass") {
            allows("GET", "https://gmm-games.https.gsmsv.site") shouldBe true
            allows("OPTIONS", "https://gmm-games.https.gsmsv.site") shouldBe true
            allows("POST", null) shouldBe true
        }

        test("a rejected request becomes a 403 error response and never reaches the controller") {
            val controller = ProbeController()
            val mvc = MockMvcBuilders
                .standaloneSetup(controller)
                .addMappedInterceptors(arrayOf("/api/v1/**"), interceptor)
                .setControllerAdvice(GlobalExceptionHandler(properties))
                .build()

            mvc.perform(post("/api/v1/probe").header("Origin", "https://gmm-games.https.gsmsv.site"))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("CROSS_SITE_REQUEST"))
            controller.writes shouldBe 0

            mvc.perform(post("/api/v1/probe").header("Origin", frontend)).andExpect(status().isOk)
            mvc.perform(get("/api/v1/probe").header("Origin", "https://gmm-games.https.gsmsv.site"))
                .andExpect(status().isOk)
            controller.writes shouldBe 1
        }
    })

@RestController
private class ProbeController {
    var writes = 0

    @PostMapping("/api/v1/probe")
    fun write(): String {
        writes++
        return "ok"
    }

    @GetMapping("/api/v1/probe")
    fun read(): String = "ok"
}
