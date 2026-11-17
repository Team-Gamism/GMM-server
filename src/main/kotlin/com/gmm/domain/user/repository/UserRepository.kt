package com.gmm.domain.user.repository

import com.gmm.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, String> {
    fun findByDataGsmId(dataGsmId: Long): User?
}
