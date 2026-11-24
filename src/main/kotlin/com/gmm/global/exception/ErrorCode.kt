package com.gmm.global.exception

import org.springframework.http.HttpStatus

/**
 * API error codes. The frontend shows [message] to the user as is, so every message is a
 * user-facing Korean sentence in 해요체.
 */
enum class ErrorCode(val status: HttpStatus, val message: String) {
    // Common
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요."),
    ADMIN_ONLY(HttpStatus.FORBIDDEN, "어드민만 할 수 있어요."),
    CROSS_SITE_REQUEST(HttpStatus.FORBIDDEN, "GMM 화면에서 보낸 요청만 처리할 수 있어요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "페이지를 찾을 수 없어요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청이에요."),
    CONFLICT(HttpStatus.CONFLICT, "요청이 겹쳤어요. 다시 시도해 주세요."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식이에요."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 용량이 너무 커요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "문제가 생겼어요. 잠시 후 다시 시도해 주세요."),

    // Auth
    INVALID_REDIRECT_URI(HttpStatus.BAD_REQUEST, "허용되지 않은 로그인 주소예요."),
    LOGIN_EXPIRED(HttpStatus.BAD_REQUEST, "로그인 시간이 지났어요. 다시 로그인해 주세요."),
    LOGIN_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "로그인 요청이 너무 많아요. 잠시 후 다시 시도해 주세요."),
    LOGIN_FAILED(HttpStatus.BAD_GATEWAY, "DataGSM 로그인에 실패했어요. 잠시 후 다시 시도해 주세요."),
    STUDENT_ONLY(HttpStatus.FORBIDDEN, "학생 계정만 로그인할 수 있어요."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없어요."),
    INVALID_AVATAR(HttpStatus.BAD_REQUEST, "프로필 사진 주소가 올바르지 않아요. 이미지를 다시 올려 주세요."),

    // Jam
    JAM_NOT_FOUND(HttpStatus.NOT_FOUND, "게임잼을 찾을 수 없어요."),
    JAM_NOT_ONGOING(HttpStatus.BAD_REQUEST, "진행 중인 게임잼에만 업로드할 수 있어요."),
    INVALID_JAM_INPUT(HttpStatus.BAD_REQUEST, "게임잼 정보가 올바르지 않아요."),
    JAM_NOT_FINISHED(HttpStatus.BAD_REQUEST, "게임잼이 끝난 뒤에 결과를 발표할 수 있어요."),
    INVALID_AWARD(HttpStatus.BAD_REQUEST, "수상 정보가 올바르지 않아요."),

    // Game
    GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "게임을 찾을 수 없어요."),
    GAME_NOT_OWNER(HttpStatus.FORBIDDEN, "내 게임만 수정할 수 있어요."),
    GAME_DELETE_NOT_OWNER(HttpStatus.FORBIDDEN, "내 게임만 삭제할 수 있어요."),
    INVALID_UPLOAD_STATUS(HttpStatus.BAD_REQUEST, "업로드 확인 상태가 올바르지 않아요."),
    INVALID_GAME_INPUT(HttpStatus.BAD_REQUEST, "게임 정보가 올바르지 않아요."),

    // Review
    REVIEW_OWN_GAME(HttpStatus.BAD_REQUEST, "내 게임은 평가할 수 없어요."),
    REVIEW_NOT_OPEN(HttpStatus.BAD_REQUEST, "지금은 평가 기간이 아니에요."),
    INVALID_REVIEW_INPUT(HttpStatus.BAD_REQUEST, "모든 항목에 별점을 매겨주세요."),
    REVIEW_NOT_AUTHOR(HttpStatus.FORBIDDEN, "내 평가만 삭제할 수 있어요."),

    // Image
    IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "이미지 파일을 첨부해 주세요."),
    IMAGE_INVALID(HttpStatus.BAD_REQUEST, "JPEG, PNG, WebP 이미지만 올릴 수 있어요."),
    IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "이미지는 5MB 이하만 올릴 수 있어요."),

    // Build
    BUILD_REQUIRED(HttpStatus.BAD_REQUEST, "빌드 zip 파일을 첨부해 주세요."),
    BUILD_INVALID(HttpStatus.BAD_REQUEST, "빌드 zip 파일이 올바르지 않아요."),
    BUILD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "빌드 용량이 너무 커요."),
}
