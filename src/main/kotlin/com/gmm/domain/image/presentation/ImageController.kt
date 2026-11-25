package com.gmm.domain.image.presentation

import com.gmm.domain.image.service.ImageService
import com.gmm.global.auth.AuthUser
import com.gmm.global.auth.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@Tag(name = "Image", description = "이미지 업로드")
@RestController
@RequestMapping("/uploads")
class ImageController(private val imageService: ImageService) {
    @Operation(
        summary = "이미지 업로드",
        description = "아이콘·대표 이미지·프로필 사진을 먼저 올리고, 받은 url을 각 API의 JSON에 넣어요. JPEG·PNG·WebP, 기본 5MB 이하.",
    )
    @PostMapping("/images", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@CurrentUser user: AuthUser, @RequestPart("file") file: MultipartFile): ResponseEntity<ImageResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(ImageResponse(imageService.upload(file)))
}

data class ImageResponse(val url: String)
