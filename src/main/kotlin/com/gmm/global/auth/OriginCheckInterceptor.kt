package com.gmm.global.auth

import com.gmm.global.config.GmmProperties
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Rejects state-changing API requests that a browser sent from a page outside the GMM frontend.
 *
 * Builds are served from a sibling subdomain (`gmm-games.https.gsmsv.site`) that browsers treat as the same
 * site as the frontend, so the `SameSite=Lax` session cookie does not stop an uploaded build, or another
 * project on the same parent domain, from sending a form or multipart request with the player's session.
 * Browsers always set `Origin` on such requests and pages cannot forge it. A request without `Origin` did not
 * come from a browser page, so it carries no ambient cookie and is let through.
 */
@Component
class OriginCheckInterceptor(properties: GmmProperties) : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    private val allowedOrigins: Set<String> =
        (properties.cors.allowedOrigins + properties.frontend.origin)
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .toSet()

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method.uppercase() in SAFE_METHODS) return true
        val origin = request.getHeader(HttpHeaders.ORIGIN) ?: return true
        if (normalize(origin) in allowedOrigins) return true

        log.info("Reject {} {} from origin {}", request.method, request.requestURI, origin)
        throw GmmException(ErrorCode.CROSS_SITE_REQUEST)
    }

    private companion object {
        val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS", "TRACE")

        fun normalize(origin: String): String = origin.trim().trimEnd('/').lowercase()
    }
}
