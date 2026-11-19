package com.gmm.domain.auth.presentation

import com.gmm.domain.auth.service.AuthService
import com.gmm.domain.user.presentation.UserResponse
import com.gmm.domain.user.service.UserService
import com.gmm.global.auth.AuthUser
import com.gmm.global.auth.CurrentUser
import com.gmm.global.auth.SessionCookies
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val userService: UserService,
    private val sessionCookies: SessionCookies,
) {
    /** Guests get 401; the frontend treats it as "not signed in". */
    @GetMapping("/me")
    fun me(@CurrentUser user: AuthUser): UserResponse = UserResponse.from(userService.getUser(user.id))

    @PostMapping("/datagsm/callback")
    fun dataGsmCallback(@RequestBody request: DataGsmCallbackRequest): ResponseEntity<UserResponse> {
        val result = authService.login(request.code, request.codeVerifier, request.redirectUri)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, sessionCookies.issue(result.sessionToken).toString())
            .body(UserResponse.from(result.user))
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest): ResponseEntity<Void> {
        authService.logout(sessionCookies.read(request))
        return ResponseEntity
            .noContent()
            .header(HttpHeaders.SET_COOKIE, sessionCookies.clear().toString())
            .build()
    }
}

data class DataGsmCallbackRequest(val code: String = "", val codeVerifier: String = "", val redirectUri: String = "")
