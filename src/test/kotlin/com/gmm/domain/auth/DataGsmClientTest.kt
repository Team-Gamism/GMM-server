package com.gmm.domain.auth

import com.gmm.domain.auth.client.DataGsmClient
import com.gmm.global.config.GmmProperties
import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress

class DataGsmClientTest :
    FunSpec({
        test("sends the token request as a JSON body") {
            var contentType: String? = null
            var body = ""
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/token") { exchange ->
                contentType = exchange.requestHeaders.getFirst("Content-Type")
                body = exchange.requestBody.readAllBytes().decodeToString()
                val response = """{"access_token":"at"}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            server.start()
            try {
                val properties =
                    GmmProperties(
                        datagsm = GmmProperties.DataGsm(
                            clientId = "client",
                            tokenUri = "http://127.0.0.1:${server.address.port}/token",
                        ),
                    )
                val mapper = JsonMapper()
                val client = DataGsmClient(properties, mapper)

                client.exchangeCode("code", "verifier", "https://gmm.example/auth/callback") shouldBe "at"

                contentType!! shouldStartWith "application/json"
                mapper.readValue(body, Map::class.java) shouldBe
                    mapOf(
                        "grant_type" to "authorization_code",
                        "code" to "code",
                        "client_id" to "client",
                        "redirect_uri" to "https://gmm.example/auth/callback",
                        "code_verifier" to "verifier",
                    )
            } finally {
                server.stop(0)
            }
        }
    })
