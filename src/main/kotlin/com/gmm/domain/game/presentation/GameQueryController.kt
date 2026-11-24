package com.gmm.domain.game.presentation

import com.gmm.domain.game.entity.Genre
import com.gmm.domain.game.entity.Orientation
import com.gmm.domain.game.service.GameQuery
import com.gmm.domain.game.service.GameQueryService
import com.gmm.domain.game.service.GameSort
import com.gmm.global.auth.AuthUser
import com.gmm.global.auth.CurrentUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Game", description = "게임 업로드·수정·삭제·조회")
@RestController
@RequestMapping("/games")
class GameQueryController(private val gameQueryService: GameQueryService) {
    @Operation(
        summary = "게임 목록",
        description = "기본은 확인 완료된 게임만 보여요. ownerId가 있거나 includeUnapproved=true이면 볼 권한이 있는 미승인 게임도 섞어요.",
    )
    @GetMapping
    fun list(
        @CurrentUser(required = false) user: AuthUser?,
        @RequestParam(required = false) jamId: String?,
        @RequestParam(required = false) ownerId: String?,
        @Parameter(description = "한국어 장르 이름 (예: 액션)") @RequestParam(required = false) genre: String?,
        @Parameter(description = "landscape | portrait") @RequestParam(required = false) orientation: String?,
        @Parameter(description = "제목·한 줄 소개·제작자 이름·장르 부분 일치") @RequestParam(required = false) q: String?,
        @Parameter(description = "recent(기본) | rating") @RequestParam(required = false) sort: String?,
        @RequestParam(required = false, defaultValue = "false") includeUnapproved: Boolean,
    ): List<GameSummaryResponse> = gameQueryService.list(
        user,
        GameQuery(
            jamId = jamId?.ifBlank { null },
            ownerId = ownerId?.ifBlank { null },
            genre = genre?.ifBlank { null }?.let { parse("장르가 올바르지 않아요.") { Genre.fromLabel(it) } },
            orientation = orientation?.ifBlank {
                null
            }?.let { parse("화면 방향이 올바르지 않아요.") { Orientation.fromValue(it) } },
            q = q,
            sort = when (sort?.ifBlank { null }) {
                null, "recent" -> GameSort.RECENT
                "rating" -> GameSort.RATING
                else -> throw GmmException(ErrorCode.INVALID_REQUEST, "정렬 방식이 올바르지 않아요.")
            },
            includeUnapproved = includeUnapproved,
        ),
    )

    @Operation(summary = "게임 상세", description = "확인 대기·반려 게임은 제작자와 어드민만 볼 수 있어요. 그 외에는 404예요.")
    @GetMapping("/{gameId}")
    fun get(@CurrentUser(required = false) user: AuthUser?, @PathVariable gameId: String): GameSummaryResponse =
        gameQueryService.get(user, gameId)

    private fun <T> parse(message: String, block: () -> T): T = try {
        block()
    } catch (_: IllegalArgumentException) {
        throw GmmException(ErrorCode.INVALID_REQUEST, message)
    }
}
