package com.gmm.domain.user.presentation

import com.gmm.domain.image.storage.ImageStorage
import com.gmm.domain.user.service.ProfileService
import com.gmm.domain.user.service.UserService
import com.gmm.global.auth.AuthUser
import com.gmm.global.auth.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User", description = "프로필")
@RestController
@RequestMapping("/users")
class UserController(
    private val profileService: ProfileService,
    private val userService: UserService,
    private val imageStorage: ImageStorage,
) {
    @Operation(summary = "공개 프로필", description = "제출이 마감된 게임잼의 출품작과 발표된 게임잼의 수상 기록만 보여요.")
    @GetMapping("/{userId}/profile")
    fun profile(@PathVariable userId: String): UserProfileResponse = profileService.profile(userId)

    @Operation(summary = "프로필 사진 변경", description = "이미지 업로드 API에서 받은 URL을 넣어요. null이면 기본 사진으로 돌아가요.")
    @PutMapping("/me/avatar")
    fun changeAvatar(@CurrentUser user: AuthUser, @RequestBody request: AvatarRequest): UserResponse =
        profileService.changeAvatar(user, request.normalized(imageStorage::isStoredUrl))

    @Operation(
        summary = "어드민 권한 변경",
        description = "최초 어드민(GMM_ADMIN_EMAILS)만 할 수 있어요. 최초 어드민의 권한은 바꿀 수 없어요.",
    )
    @PutMapping("/{userId}/role")
    fun changeRole(
        @CurrentUser user: AuthUser,
        @PathVariable userId: String,
        @RequestBody request: RoleRequest,
    ): UserResponse = UserResponse.from(userService.changeRole(user, userId, request.role))
}
