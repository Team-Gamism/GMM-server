package com.gmm.domain.user

import com.gmm.domain.session.entity.UserSession
import com.gmm.domain.session.repository.UserSessionRepository
import com.gmm.domain.session.service.SessionService
import com.gmm.domain.user.entity.User
import com.gmm.domain.user.entity.UserRole
import com.gmm.domain.user.repository.UserRepository
import com.gmm.domain.user.service.AdminPolicy
import com.gmm.domain.user.service.DataGsmProfile
import com.gmm.domain.user.service.UserService
import com.gmm.global.config.GmmProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class AdminRoleTest :
    FunSpec({
        val now = Instant.parse("2026-11-24T00:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)

        fun policy(vararg emails: String) = AdminPolicy(GmmProperties(admin = GmmProperties.Admin(emails.toList())))

        fun user(role: UserRole, email: String = "a@gsm.hs.kr") =
            User("u1", 1, email, "김하늘", "1101", role, now.minusSeconds(3600))

        test("only listed emails are admins, ignoring case and spaces") {
            val policy = policy(" A@gsm.hs.kr", "b@gsm.hs.kr ", "")

            policy.roleFor("a@GSM.hs.kr") shouldBe UserRole.ADMIN
            policy.roleFor("b@gsm.hs.kr") shouldBe UserRole.ADMIN
            policy.roleFor("c@gsm.hs.kr") shouldBe UserRole.USER
            policy().roleFor("a@gsm.hs.kr") shouldBe UserRole.USER
        }

        test("login promotes listed users and demotes removed admins") {
            val repository = mockk<UserRepository>()
            val existing = user(UserRole.ADMIN)
            every { repository.findByDataGsmId(1) } returns existing
            val saved = slot<User>()
            every { repository.findByDataGsmId(2) } returns null
            every { repository.save(capture(saved)) } answers { saved.captured }

            UserService(repository, policy(), clock)
                .upsertFromDataGsm(DataGsmProfile(1, "a@gsm.hs.kr", "김하늘", "1101")).role shouldBe UserRole.USER
            UserService(repository, policy("new@gsm.hs.kr"), clock)
                .upsertFromDataGsm(DataGsmProfile(2, "new@gsm.hs.kr", "새 어드민", null)).role shouldBe UserRole.ADMIN
        }

        test("every request re-checks the role, so removing an email revokes admin without a new login") {
            val sessions = mockk<UserSessionRepository>()
            val users = mockk<UserRepository>()
            val admin = user(UserRole.ADMIN)
            every { sessions.findById(any()) } returns
                Optional.of(UserSession("hash", "u1", now, now.plusSeconds(3600)))
            every { users.findById("u1") } returns Optional.of(admin)
            every { users.updateRole(any(), any(), any()) } returns 1

            fun service(vararg emails: String) =
                SessionService(sessions, users, policy(*emails), GmmProperties(), clock)

            service("a@gsm.hs.kr").authenticate("token")!!.role shouldBe UserRole.ADMIN
            verify(exactly = 0) { users.updateRole(any(), any(), any()) }

            service().authenticate("token")!!.role shouldBe UserRole.USER
            verify(exactly = 1) { users.updateRole("u1", UserRole.USER, now) }
        }
    })
