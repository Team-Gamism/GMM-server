package com.gmm.domain.user.service

import com.gmm.domain.user.entity.User
import com.gmm.domain.user.entity.UserRole
import com.gmm.domain.user.repository.UserRepository
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import com.gmm.global.support.Ids
import com.gmm.global.support.nowMillis
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class UserService(
    private val userRepository: UserRepository,
    private val adminPolicy: AdminPolicy,
    private val clock: Clock,
) {
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
        user.assignRole(adminPolicy.roleFor(profile.email), now)
        return user
    }

    @Transactional(readOnly = true)
    fun getUser(userId: String): User =
        userRepository.findById(userId).orElseThrow { GmmException(ErrorCode.UNAUTHORIZED) }
}

data class DataGsmProfile(val dataGsmId: Long, val email: String, val name: String, val studentNumber: String?)
