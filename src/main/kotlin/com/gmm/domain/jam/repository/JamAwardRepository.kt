package com.gmm.domain.jam.repository

import com.gmm.domain.jam.entity.JamAward
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface JamAwardRepository : JpaRepository<JamAward, Long> {
    fun findAllByJamIdIn(jamIds: Collection<String>): List<JamAward>

    fun findAllByGameIdIn(gameIds: Collection<String>): List<JamAward>

    /** Runs immediately, so new awards with the same (jam, game) can be inserted in the same transaction. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from JamAward a where a.jamId = :jamId")
    fun deleteAllByJamId(jamId: String): Int
}
