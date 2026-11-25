package com.gmm.domain.jam.repository

import com.gmm.domain.jam.entity.Jam
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface JamRepository : JpaRepository<Jam, String> {
    fun findAllByOrderByStartAtDesc(): List<Jam>

    /** `SELECT ... FOR UPDATE`; serializes game creation within one jam. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Jam j where j.id = :id")
    fun findForUpdate(id: String): Jam?
}
