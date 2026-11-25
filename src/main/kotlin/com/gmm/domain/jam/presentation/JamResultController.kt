package com.gmm.domain.jam.presentation

import com.gmm.domain.jam.service.JamResultService
import com.gmm.global.auth.AuthUser
import com.gmm.global.auth.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Jam", description = "게임잼")
@RestController
@RequestMapping("/jams/{jamId}")
class JamResultController(private val jamResultService: JamResultService) {
    @Operation(
        summary = "순위",
        description = "확인 완료된 제출작만 넣어요. 동점은 같은 순위(1, 2, 2, 4)이고, 평가가 없는 게임은 맨 뒤예요. " +
            "결과 발표 전에는 어드민만 볼 수 있고, 그 외에는 403 RESULTS_NOT_ANNOUNCED예요.",
    )
    @GetMapping("/ranking")
    fun ranking(
        @CurrentUser(required = false) user: AuthUser?,
        @PathVariable jamId: String,
    ): List<RankingEntryResponse> = jamResultService.ranking(user, jamId)

    @Operation(summary = "결과 발표 (어드민)", description = "게임잼이 끝난 뒤(심사 중·발표됨)에만 할 수 있어요. 다시 발표하면 수상 목록을 덮어써요.")
    @PutMapping("/results")
    fun announce(
        @CurrentUser user: AuthUser,
        @PathVariable jamId: String,
        @RequestBody request: AwardsRequest,
    ): JamResponse = jamResultService.announce(user, jamId, request)

    @Operation(summary = "결과 발표 취소 (어드민)", description = "announcedAt만 지워요. 수상 목록은 남아요.")
    @DeleteMapping("/results")
    fun cancel(@CurrentUser user: AuthUser, @PathVariable jamId: String): JamResponse =
        jamResultService.cancel(user, jamId)
}
