package com.gmm.domain.user.repository

import com.gmm.domain.user.entity.User
import com.gmm.domain.user.entity.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface UserRepository : JpaRepository<User, String> {
    fun findByDataGsmId(dataGsmId: Long): User?

    @Transactional
    @Modifying
    @Query("update User u set u.role = :role, u.updatedAt = :now where u.id = :id")
    fun updateRole(id: String, role: UserRole, now: Instant): Int
}
