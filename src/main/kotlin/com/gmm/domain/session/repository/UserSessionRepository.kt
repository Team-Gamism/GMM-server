package com.gmm.domain.session.repository

import com.gmm.domain.session.entity.UserSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface UserSessionRepository : JpaRepository<UserSession, String> {
    @Modifying
    @Query("delete from UserSession s where s.expiresAt <= :now")
    fun deleteExpired(now: Instant): Int
}
