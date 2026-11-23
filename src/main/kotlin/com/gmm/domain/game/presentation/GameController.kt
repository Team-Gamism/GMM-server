package com.gmm.domain.game.presentation

import com.gmm.domain.game.service.GameUploadService
import com.gmm.global.auth.AuthUser
import com.gmm.global.auth.CurrentUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Encoding
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.Part
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import io.swagger.v3.oas.annotations.parameters.RequestBody as DocRequestBody

@Tag(name = "Game", description = "게임 업로드·수정")
@RestController
@RequestMapping("/games")
class GameController(private val gameUploadService: GameUploadService) {
    @Operation(summary = "게임 업로드", description = "진행 중인 게임잼에만 올릴 수 있어요(어드민 제외). 새 게임은 확인 대기 상태예요.")
    @DocRequestBody(
        content = [
            Content(
                mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                schema = Schema(implementation = GameUploadForm::class),
                encoding = [Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)],
            ),
        ],
    )
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun create(
        @CurrentUser user: AuthUser,
        @Parameter(hidden = true) @RequestPart("data") data: Part,
        @Parameter(hidden = true) @RequestPart("buildFile") build: MultipartFile,
    ): ResponseEntity<GameResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(gameUploadService.create(user, readText(data), build))

    @Operation(summary = "게임 수정", description = "본인 또는 어드민만 수정해요. 빌드를 새로 올리면 다시 확인 대기가 돼요.")
    @DocRequestBody(
        content = [
            Content(
                mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                schema = Schema(implementation = GameUploadForm::class),
                encoding = [Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)],
            ),
        ],
    )
    @PutMapping("/{gameId}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun update(
        @CurrentUser user: AuthUser,
        @PathVariable gameId: String,
        @Parameter(hidden = true) @RequestPart("data") data: Part,
        @Parameter(hidden = true) @RequestPart("buildFile", required = false) build: MultipartFile?,
    ): GameResponse = gameUploadService.update(user, gameId, readText(data), build)

    /**
     * Reads the `data` part as UTF-8 whether the client sent a plain form field or a JSON blob.
     * A `String` parameter would fall back to ISO-8859-1 for parts without a charset.
     */
    private fun readText(part: Part): String {
        if (part.size > MAX_DATA_BYTES) throw GmmException(ErrorCode.INVALID_GAME_INPUT, "게임 정보가 너무 길어요.")
        return part.inputStream.use { it.readAllBytes() }.toString(Charsets.UTF_8)
    }

    companion object {
        private const val MAX_DATA_BYTES = 100 * 1024L
    }
}
