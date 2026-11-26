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
import com.gmm.global.auth.AuthUser
import com.gmm.global.config.GmmProperties
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import io.kotest.assertions.throwables.shouldThrow
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

        test("listed emails are owners, ignoring case and spaces; others keep the stored role") {
            val policy = policy(" A@gsm.hs.kr", "b@gsm.hs.kr ", "")

            policy.roleFor("a@GSM.hs.kr", UserRole.USER) shouldBe UserRole.ADMIN
            policy.roleFor("b@gsm.hs.kr", UserRole.USER) shouldBe UserRole.ADMIN
            policy.roleFor("c@gsm.hs.kr", UserRole.ADMIN) shouldBe UserRole.ADMIN
            policy.roleFor("c@gsm.hs.kr", UserRole.USER) shouldBe UserRole.USER
            policy().isOwner("a@gsm.hs.kr") shouldBe false
        }

        test("login promotes owners and keeps a granted admin") {
            val repository = mockk<UserRepository>()
            every { repository.findByDataGsmId(1) } returns user(UserRole.ADMIN)
            val saved = slot<User>()
            every { repository.findByDataGsmId(2) } returns null
            every { repository.save(capture(saved)) } answers { saved.captured }

            UserService(repository, policy(), clock)
                .upsertFromDataGsm(DataGsmProfile(1, "a@gsm.hs.kr", "김하늘", "1101")).role shouldBe UserRole.ADMIN
            UserService(repository, policy("new@gsm.hs.kr"), clock)
                .upsertFromDataGsm(DataGsmProfile(2, "new@gsm.hs.kr", "새 어드민", null)).role shouldBe UserRole.ADMIN
        }

        test("every request reads the stored role and promotes owners without a new login") {
            val sessions = mockk<UserSessionRepository>()
            val users = mockk<UserRepository>()
            var stored = user(UserRole.USER)
            every { sessions.findById(any()) } returns
                Optional.of(UserSession("hash", "u1", now, now.plusSeconds(3600)))
            every { users.findById("u1") } answers { Optional.of(stored) }
            every { users.updateRole(any(), any(), any()) } returns 1

            fun service(vararg emails: String) =
                SessionService(sessions, users, policy(*emails), GmmProperties(), clock)

            service().authenticate("token")!!.role shouldBe UserRole.USER
            verify(exactly = 0) { users.updateRole(any(), any(), any()) }

            service("a@gsm.hs.kr").authenticate("token")!!.role shouldBe UserRole.ADMIN
            verify(exactly = 1) { users.updateRole("u1", UserRole.ADMIN, now) }

            stored = user(UserRole.ADMIN)
            service().authenticate("token")!!.role shouldBe UserRole.ADMIN
        }

        context("changeRole") {
            val owner = User("owner", 10, "owner@gsm.hs.kr", "오너", null, UserRole.ADMIN, now)
            val admin = User("admin", 11, "admin@gsm.hs.kr", "어드민", null, UserRole.ADMIN, now)

            fun service(target: User): Pair<UserService, User> {
                val users = mockk<UserRepository>()
                every { users.findById(owner.id) } returns Optional.of(owner)
                every { users.findById(admin.id) } returns Optional.of(admin)
                every { users.findById(target.id) } returns Optional.of(target)
                every { users.findById("missing") } returns Optional.empty()
                return UserService(users, policy("owner@gsm.hs.kr"), clock) to target
            }

            val ownerCaller = AuthUser(owner.id, UserRole.ADMIN)

            test("an owner grants and revokes admin") {
                val (service, target) = service(user(UserRole.USER))
                service.changeRole(ownerCaller, "u1", UserRole.ADMIN).role shouldBe UserRole.ADMIN
                target.role shouldBe UserRole.ADMIN
                service.changeRole(ownerCaller, "u1", UserRole.USER).role shouldBe UserRole.USER
            }

            test("a non-owner admin cannot change roles") {
                val (service, target) = service(user(UserRole.USER))
                shouldThrow<GmmException> {
                    service.changeRole(AuthUser(admin.id, UserRole.ADMIN), "u1", UserRole.ADMIN)
                }.code shouldBe ErrorCode.OWNER_ONLY
                target.role shouldBe UserRole.USER
            }

            test("an owner cannot be demoted and a missing user is rejected") {
                val (service, _) = service(user(UserRole.USER))
                shouldThrow<GmmException> {
                    service.changeRole(ownerCaller, owner.id, UserRole.USER)
                }.code shouldBe ErrorCode.OWNER_ROLE_FIXED
                shouldThrow<GmmException> {
                    service.changeRole(ownerCaller, "missing", UserRole.ADMIN)
                }.code shouldBe ErrorCode.USER_NOT_FOUND
            }
        }
    })
