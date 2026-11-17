package com.gmm.domain.user.presentation

import com.gmm.domain.user.service.UserService
import com.gmm.global.auth.AuthUser
import com.gmm.global.auth.CurrentUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {
    @GetMapping("/me")
    fun me(@CurrentUser user: AuthUser): UserResponse = UserResponse.from(userService.getUser(user.id))
}
