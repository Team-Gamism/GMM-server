package com.gmm.domain.game.repository

import com.gmm.domain.game.entity.Game
import com.gmm.domain.game.entity.ReviewStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface GameRepository : JpaRepository<Game, String> {
    @Query("select g.buildPath from Game g where g.buildPath is not null")
    fun findAllBuildPaths(): List<String>

    @Query("select g.id from Game g where g.jamId = :jamId")
    fun findIdsByJamId(jamId: String): List<String>

    fun findAllByJamIdAndReviewStatusOrderByCreatedAtAsc(jamId: String, reviewStatus: ReviewStatus): List<Game>
}
