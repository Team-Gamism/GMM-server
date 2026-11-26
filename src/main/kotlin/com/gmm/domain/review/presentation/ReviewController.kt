package com.gmm.domain.review.presentation

import com.gmm.domain.review.service.ReviewService
import com.gmm.global.auth.AuthUser
import com.gmm.global.auth.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Review", description = "평가")
@RestController
class ReviewController(private val reviewService: ReviewService) {
    @Operation(summary = "게임의 평가 목록", description = "최신순이에요. 미승인 게임은 제작자와 어드민만 볼 수 있어요.")
    @GetMapping("/games/{gameId}/reviews")
    fun list(
        @CurrentUser(required = false) user: AuthUser?,
        @PathVariable gameId: String,
    ): List<ReviewWithAuthorResponse> = reviewService.list(user, gameId)

    @Operation(
        summary = "내 평가 작성·수정",
        description = "게임당 1개예요. 내 게임이거나 평가 기간(제출 마감 뒤 reviewDays일)이 아니면 400이에요.",
    )
    @PutMapping("/games/{gameId}/reviews/me")
    fun upsertMine(
        @CurrentUser user: AuthUser,
        @PathVariable gameId: String,
        @RequestBody request: ReviewRequest,
    ): ReviewResponse = reviewService.upsertMine(user, gameId, request)

    @Operation(summary = "평가 삭제", description = "작성자 또는 어드민만 지울 수 있어요.")
    @DeleteMapping("/reviews/{reviewId}")
    fun delete(@CurrentUser user: AuthUser, @PathVariable reviewId: String): ResponseEntity<Void> {
        reviewService.delete(user, reviewId)
        return ResponseEntity.noContent().build()
    }
}
