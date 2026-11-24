package com.gmm.domain.health.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.health.contributor.Status
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public service status for the frontend and uptime checks. It reuses the actuator health checks (database, disk)
 * but returns only the overall status, never the details.
 */
@Tag(name = "Health", description = "서비스 상태")
@RestController
@RequestMapping("/health")
class HealthController(private val healthEndpoint: HealthEndpoint) {
    @Operation(summary = "서비스 상태 조회", description = "DB 연결과 디스크를 포함한 전체 상태예요. 로그인이 필요 없어요.")
    @ApiResponse(responseCode = "200", description = "정상이에요. `status`가 `UP`이에요.")
    @ApiResponse(responseCode = "503", description = "일부 검사가 실패했어요. `status`가 `DOWN`이에요.")
    @GetMapping
    fun health(): ResponseEntity<HealthResponse> = if (healthEndpoint.health().status == Status.UP) {
        ResponseEntity.ok(HealthResponse(HealthResponse.UP))
    } else {
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(HealthResponse(HealthResponse.DOWN))
    }
}

data class HealthResponse(val status: String) {
    companion object {
        const val UP = "UP"
        const val DOWN = "DOWN"
    }
}
