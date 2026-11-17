package com.gmm.domain.auth.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class DataGsmTokenResponse(
    @field:JsonProperty("access_token")
    val accessToken: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DataGsmErrorResponse(
    val error: String? = null,
    @field:JsonProperty("error_description")
    val errorDescription: String? = null,
)

/** Subset of `GET https://oauth.resource.datagsm.kr/userinfo` that GMM uses. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DataGsmUserInfo(
    val id: Long? = null,
    val email: String? = null,
    /** `STUDENT`, `TEACHER`, or null when the account links to neither. */
    val objectType: String? = null,
    val student: Student? = null,
    val teacher: Teacher? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Student(
        val name: String? = null,
        /** Four digits such as 1101. Null while an admin correction request is pending. */
        val studentNumber: Int? = null,
        /** `GENERAL_STUDENT`, `STUDENT_COUNCIL`, `DORMITORY_MANAGER`, `GRADUATE`, `WITHDRAWN`. */
        val role: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Teacher(val name: String? = null)
}
