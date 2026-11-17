package com.gmm.domain.game.entity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/** The API uses the Korean label. The database stores the enum name. */
enum class Genre(@get:JsonValue val label: String) {
    ACTION("액션"),
    ADVENTURE("어드벤처"),
    PUZZLE("퍼즐"),
    PLATFORMER("플랫포머"),
    SHOOTING("슈팅"),
    RPG("RPG"),
    STRATEGY("전략"),
    SIMULATION("시뮬레이션"),
    RHYTHM("리듬"),
    CASUAL("캐주얼"),
    HORROR("공포"),
    OTHER("기타"),
    ;

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromLabel(label: String): Genre =
            entries.firstOrNull { it.label == label } ?: throw IllegalArgumentException("Unknown genre")
    }
}

enum class Orientation(@get:JsonValue val value: String) {
    LANDSCAPE("landscape"),
    PORTRAIT("portrait"),
    ;

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): Orientation =
            entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("Unknown orientation")
    }
}

enum class ReviewStatus(@get:JsonValue val value: String) {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
}

enum class Platform(@get:JsonValue val label: String) {
    PC("PC"),
    MOBILE("모바일"),
    ;

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromLabel(label: String): Platform =
            entries.firstOrNull { it.label == label } ?: throw IllegalArgumentException("Unknown platform")
    }
}

enum class InputDevice(@get:JsonValue val label: String) {
    KEYBOARD("키보드"),
    MOUSE("마우스"),
    TOUCH("터치"),
    GAMEPAD("게임패드"),
    ;

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromLabel(label: String): InputDevice =
            entries.firstOrNull { it.label == label } ?: throw IllegalArgumentException("Unknown input device")
    }
}
