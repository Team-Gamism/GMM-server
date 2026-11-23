package com.gmm.domain.jam.repository

import com.gmm.domain.jam.entity.JamAward
import org.springframework.data.jpa.repository.JpaRepository

interface JamAwardRepository : JpaRepository<JamAward, Long> {
    fun findAllByJamIdIn(jamIds: Collection<String>): List<JamAward>
}
