package com.gmm.domain.user.entity

import com.fasterxml.jackson.annotation.JsonValue

enum class UserRole(@get:JsonValue val value: String) {
    USER("user"),
    ADMIN("admin"),
}
