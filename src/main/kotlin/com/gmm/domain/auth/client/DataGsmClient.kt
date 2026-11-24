package com.gmm.domain.auth.client

import com.gmm.global.config.GmmProperties
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient

/**
 * Server-side half of the DataGSM OAuth flow (Authorization Code + PKCE).
 * The token endpoint blocks browsers through CORS, so the server must call it.
 */
@Component
class DataGsmClient(private val properties: GmmProperties, private val jsonMapper: JsonMapper) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient =
        RestClient
            .builder()
            .requestFactory(
                JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(properties.datagsm.connectTimeout).build(),
                ).apply { setReadTimeout(properties.datagsm.readTimeout) },
            ).build()

    fun exchangeCode(code: String, codeVerifier: String, redirectUri: String): String {
        val clientId = properties.datagsm.clientId
        if (clientId.isBlank()) {
            log.error("Fail DataGSM login because gmm.datagsm.client-id is not set")
            throw GmmException(ErrorCode.LOGIN_FAILED)
        }
        // DataGSM reads only a JSON body here; a form body is rejected as "grant_type은 필수입니다".
        val body =
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "code_verifier" to codeVerifier,
            )
        val response =
            call("token") {
                restClient
                    .post()
                    .uri(properties.datagsm.tokenUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
            }
        if (response.status.is2xxSuccessful) {
            return parse<DataGsmTokenResponse>("token", response.body).accessToken?.takeIf { it.isNotBlank() }
                ?: failWith("token", "response has no access_token")
        }
        if (response.status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            throw GmmException(ErrorCode.LOGIN_RATE_LIMITED)
        }
        val error = runCatching { parse<DataGsmErrorResponse>("token", response.body).error }.getOrNull()
        if (response.status.value() == HttpStatus.BAD_REQUEST.value() && error == "invalid_grant") {
            throw GmmException(ErrorCode.LOGIN_EXPIRED)
        }
        failWith("token", "status ${response.status.value()}, error $error")
    }

    fun fetchUserInfo(accessToken: String): DataGsmUserInfo {
        val response =
            call("userinfo") {
                restClient
                    .get()
                    .uri(properties.datagsm.userInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .accept(MediaType.APPLICATION_JSON)
            }
        if (response.status.is2xxSuccessful) return parse("userinfo", response.body)
        if (response.status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            throw GmmException(ErrorCode.LOGIN_RATE_LIMITED)
        }
        failWith("userinfo", "status ${response.status.value()}")
    }

    private fun call(endpoint: String, request: () -> RestClient.RequestHeadersSpec<*>): RawResponse = try {
        request().exchange { _, res -> RawResponse(res.statusCode, res.body.readAllBytes()) }
            ?: failWith(endpoint, "empty response")
    } catch (e: RestClientException) {
        log.warn("Fail to call DataGSM {}", endpoint, e)
        throw GmmException(ErrorCode.LOGIN_FAILED)
    }

    private inline fun <reified T : Any> parse(endpoint: String, body: ByteArray): T = try {
        jsonMapper.readValue(body, T::class.java)
    } catch (e: JacksonException) {
        log.warn("Fail to parse DataGSM {} response", endpoint, e)
        throw GmmException(ErrorCode.LOGIN_FAILED)
    }

    private fun failWith(endpoint: String, reason: String): Nothing {
        log.warn("Reject DataGSM {} response: {}", endpoint, reason)
        throw GmmException(ErrorCode.LOGIN_FAILED)
    }

    private class RawResponse(val status: HttpStatusCode, val body: ByteArray)
}
