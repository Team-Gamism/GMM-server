package com.gmm.domain.auth

import com.gmm.domain.auth.client.DataGsmClient
import com.gmm.domain.auth.client.DataGsmUserInfo
import com.gmm.domain.auth.service.AuthService
import com.gmm.global.config.GmmProperties
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify

class AuthServiceTest :
    FunSpec({
        val client = mockk<DataGsmClient>()
        val redirect = "http://localhost:5173/auth/callback"

        fun service(allowTeacher: Boolean = false) = AuthService(
            client,
            mockk(),
            mockk(),
            GmmProperties(
                datagsm = GmmProperties.DataGsm(
                    allowedRedirectUris = listOf(redirect),
                    allowTeacher = allowTeacher,
                ),
            ),
        )

        fun student(number: Int? = 1101, role: String = "GENERAL_STUDENT") = DataGsmUserInfo(
            id = 1,
            email = "student@gsm.hs.kr",
            objectType = "STUDENT",
            student = DataGsmUserInfo.Student(name = "홍길동", studentNumber = number, role = role),
        )

        val teacher =
            DataGsmUserInfo(
                id = 2,
                email = "teacher@gsm.hs.kr",
                objectType = "TEACHER",
                teacher = DataGsmUserInfo.Teacher(name = "김선생"),
            )

        test("maps a student and formats the student number as four digits") {
            val profile = service().toProfile(student(number = 1101))

            profile.dataGsmId shouldBe 1
            profile.name shouldBe "홍길동"
            profile.studentNumber shouldBe "1101"
        }

        test("drops the student number of graduates and withdrawn students") {
            service().toProfile(student(role = "GRADUATE")).studentNumber shouldBe null
            service().toProfile(student(role = "WITHDRAWN")).studentNumber shouldBe null
            service().toProfile(student(number = null)).studentNumber shouldBe null
        }

        test("rejects teachers unless allowed") {
            shouldThrow<GmmException> { service().toProfile(teacher) }.code shouldBe ErrorCode.STUDENT_ONLY

            val profile = service(allowTeacher = true).toProfile(teacher)
            profile.name shouldBe "김선생"
            profile.studentNumber shouldBe null
        }

        test("fails when the student scope is missing") {
            shouldThrow<GmmException> { service().toProfile(student().copy(student = null)) }.code shouldBe
                ErrorCode.LOGIN_FAILED
        }

        test("rejects unknown redirect URIs and malformed verifiers before calling DataGSM") {
            val verifier = "a".repeat(43)

            shouldThrow<GmmException> { service().login("code", verifier, "https://evil.example/cb") }.code shouldBe
                ErrorCode.INVALID_REDIRECT_URI
            shouldThrow<GmmException> { service().login("code", "short", redirect) }.code shouldBe
                ErrorCode.INVALID_REQUEST
            verify(exactly = 0) { client.exchangeCode(any(), any(), any()) }
        }
    })
