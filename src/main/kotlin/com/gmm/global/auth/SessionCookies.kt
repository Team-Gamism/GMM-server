package com.gmm.global.auth

import com.gmm.global.config.GmmProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SessionCookies(private val properties: GmmProperties) {
    fun read(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == properties.session.cookieName }?.value

    fun issue(token: String): ResponseCookie = build(token, properties.session.ttl)

    fun clear(): ResponseCookie = build("", Duration.ZERO)

    private fun build(value: String, maxAge: Duration): ResponseCookie = ResponseCookie
        .from(properties.session.cookieName, value)
        .httpOnly(true)
        .secure(properties.session.cookieSecure)
        .sameSite("Lax")
        .path("/")
        .maxAge(maxAge)
        .build()
}
