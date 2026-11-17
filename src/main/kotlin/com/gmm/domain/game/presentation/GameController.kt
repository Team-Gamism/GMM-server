package com.gmm.domain.game.presentation

import com.gmm.domain.game.service.GameUploadService
import com.gmm.global.auth.AuthUser
import com.gmm.global.auth.CurrentUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
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

@RestController
@RequestMapping("/games")
class GameController(private val gameUploadService: GameUploadService) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun create(
        @CurrentUser user: AuthUser,
        @RequestPart("data") data: Part,
        @RequestPart("build") build: MultipartFile,
    ): ResponseEntity<GameResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(gameUploadService.create(user, readText(data), build))

    @PutMapping("/{gameId}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun update(
        @CurrentUser user: AuthUser,
        @PathVariable gameId: String,
        @RequestPart("data") data: Part,
        @RequestPart("build", required = false) build: MultipartFile?,
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
