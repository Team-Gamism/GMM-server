package com.gmm.domain.game.presentation

import io.swagger.v3.oas.annotations.media.Schema

/** API docs only: the multipart body of `POST /games` and `PUT /games/{gameId}`. */
@Schema(name = "GameUploadForm")
class GameUploadForm(
    @field:Schema(description = "게임 정보 JSON 문자열. 폼 필드나 JSON Blob 둘 다 받아요. 수정할 때 jamId는 무시해요.")
    val data: GameRequest,
    @field:Schema(
        type = "string",
        format = "binary",
        description = "Unity WebGL 빌드 zip. 생성할 때 필수, 수정할 때 빼면 기존 빌드를 유지해요.",
    )
    val buildFile: String?,
)
