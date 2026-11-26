package com.gmm.domain.game.repository

import com.gmm.domain.game.entity.Game
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface GameRepository : JpaRepository<Game, String> {
    @Query("select g.buildPath from Game g where g.buildPath is not null")
    fun findAllBuildPaths(): List<String>

    @Query("select g.id from Game g where g.jamId = :jamId")
    fun findIdsByJamId(jamId: String): List<String>

    fun countByJamIdAndOwnerId(jamId: String, ownerId: String): Long

    /** Games of the jam and owner; a null argument matches all. */
    @Query(
        "select g from Game g where (:jamId is null or g.jamId = :jamId) and (:ownerId is null or g.ownerId = :ownerId)",
    )
    fun findAllByJamAndOwner(jamId: String?, ownerId: String?): List<Game>

    fun findAllByOwnerIdOrderByCreatedAtDesc(ownerId: String): List<Game>

    fun findAllByJamIdOrderByCreatedAtAsc(jamId: String): List<Game>
}
