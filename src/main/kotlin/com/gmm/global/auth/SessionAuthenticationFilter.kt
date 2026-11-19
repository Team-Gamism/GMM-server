package com.gmm.global.auth

import com.gmm.domain.session.service.SessionService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Resolves the session cookie into an [AuthUser] request attribute. It never rejects a request itself. */
@Component
class SessionAuthenticationFilter(
    private val sessionService: SessionService,
    private val sessionCookies: SessionCookies,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        sessionCookies.read(request)?.let(sessionService::authenticate)?.let {
            request.setAttribute(AUTH_USER_ATTRIBUTE, it)
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val AUTH_USER_ATTRIBUTE = "com.gmm.authUser"
    }
}
