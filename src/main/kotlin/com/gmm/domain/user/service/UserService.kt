package com.gmm.domain.user.service

import com.gmm.domain.user.entity.User
import com.gmm.domain.user.entity.UserRole
import com.gmm.domain.user.repository.UserRepository
import com.gmm.global.auth.AuthUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import com.gmm.global.support.Ids
import com.gmm.global.support.nowMillis
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class UserService(
    private val userRepository: UserRepository,
    private val adminPolicy: AdminPolicy,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Creates the user on first login, otherwise refreshes the DataGSM-owned fields. */
    @Transactional
    fun upsertFromDataGsm(profile: DataGsmProfile): User {
        val now = clock.nowMillis()
        val user =
            userRepository.findByDataGsmId(profile.dataGsmId)?.also {
                it.refreshProfile(profile.email, profile.name, profile.studentNumber, now)
            } ?: userRepository.save(
                User(
                    id = Ids.new(),
                    dataGsmId = profile.dataGsmId,
                    email = profile.email,
                    name = profile.name,
                    studentNumber = profile.studentNumber,
                    role = UserRole.USER,
                    createdAt = now,
                ),
            )
        user.assignRole(adminPolicy.roleFor(profile.email, user.role), now)
        return user
    }

    /** Only owners change roles, and an owner's own admin role cannot be revoked. */
    @Transactional
    fun changeRole(caller: AuthUser, targetId: String, role: UserRole): User {
        val callerEmail = userRepository.findById(caller.id).orElseThrow { GmmException(ErrorCode.UNAUTHORIZED) }.email
        if (!adminPolicy.isOwner(callerEmail)) throw GmmException(ErrorCode.OWNER_ONLY)
        val target = userRepository.findById(targetId).orElseThrow { GmmException(ErrorCode.USER_NOT_FOUND) }
        if (adminPolicy.isOwner(target.email) && role != UserRole.ADMIN) throw GmmException(ErrorCode.OWNER_ROLE_FIXED)
        if (target.assignRole(role, clock.nowMillis())) {
            log.info("Owner {} changed role of user {} to {}", caller.id, target.id, role)
        }
        return target
    }

    @Transactional(readOnly = true)
    fun getUser(userId: String): User =
        userRepository.findById(userId).orElseThrow { GmmException(ErrorCode.UNAUTHORIZED) }
}

data class DataGsmProfile(val dataGsmId: Long, val email: String, val name: String, val studentNumber: String?)
