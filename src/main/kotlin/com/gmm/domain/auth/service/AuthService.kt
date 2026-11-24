package com.gmm.domain.auth.service

import com.gmm.domain.auth.client.DataGsmClient
import com.gmm.domain.auth.client.DataGsmUserInfo
import com.gmm.domain.session.service.SessionService
import com.gmm.domain.user.entity.User
import com.gmm.domain.user.service.DataGsmProfile
import com.gmm.domain.user.service.UserService
import com.gmm.global.config.GmmProperties
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Not transactional on purpose: the DataGSM calls must not hold a database transaction open. */
@Service
class AuthService(
    private val dataGsmClient: DataGsmClient,
    private val userService: UserService,
    private val sessionService: SessionService,
    private val properties: GmmProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun login(code: String, codeVerifier: String, redirectUri: String): LoginResult {
        if (code.isBlank() || code.length > MAX_CODE_LENGTH || !CODE_VERIFIER.matches(codeVerifier)) {
            throw GmmException(ErrorCode.INVALID_REQUEST)
        }
        if (redirectUri !in properties.datagsm.allowedRedirectUris) {
            throw GmmException(ErrorCode.INVALID_REDIRECT_URI)
        }
        val accessToken = dataGsmClient.exchangeCode(code, codeVerifier, redirectUri)
        val profile = toProfile(dataGsmClient.fetchUserInfo(accessToken))
        val user = userService.upsertFromDataGsm(profile)
        return LoginResult(user, sessionService.create(user.id))
    }

    fun logout(token: String?) {
        if (token != null) sessionService.revoke(token)
    }

    fun toProfile(info: DataGsmUserInfo): DataGsmProfile {
        val id = info.id ?: rejectUserInfo("missing id")
        val email = info.email?.takeIf { it.isNotBlank() } ?: rejectUserInfo("missing email")
        return when (info.objectType) {
            "STUDENT" -> {
                val student = info.student ?: rejectUserInfo("missing student; check the student_read scope")
                DataGsmProfile(
                    dataGsmId = id,
                    email = email,
                    name = student.name?.takeIf { it.isNotBlank() } ?: rejectUserInfo("missing student name"),
                    studentNumber =
                    student.studentNumber
                        ?.takeIf { student.role !in LEFT_SCHOOL_ROLES }
                        ?.let { "%04d".format(it) },
                )
            }

            "TEACHER" -> {
                if (!properties.datagsm.allowTeacher) throw GmmException(ErrorCode.STUDENT_ONLY)
                DataGsmProfile(
                    dataGsmId = id,
                    email = email,
                    name = info.teacher?.name?.takeIf { it.isNotBlank() } ?: rejectUserInfo("missing teacher name"),
                    studentNumber = null,
                )
            }

            else -> {
                throw GmmException(ErrorCode.STUDENT_ONLY)
            }
        }
    }

    private fun rejectUserInfo(reason: String): Nothing {
        log.warn("Reject DataGSM userinfo: {}", reason)
        throw GmmException(ErrorCode.LOGIN_FAILED)
    }

    data class LoginResult(val user: User, val sessionToken: String)

    companion object {
        private const val MAX_CODE_LENGTH = 2048

        /** RFC 7636 code_verifier: 43–128 unreserved characters. */
        private val CODE_VERIFIER = Regex("^[A-Za-z0-9._~-]{43,128}$")
        private val LEFT_SCHOOL_ROLES = setOf("GRADUATE", "WITHDRAWN")
    }
}
