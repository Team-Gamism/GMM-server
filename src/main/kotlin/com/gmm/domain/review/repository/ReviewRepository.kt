package com.gmm.domain.review.repository

import com.gmm.domain.review.entity.Review
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ReviewRepository : JpaRepository<Review, String> {
    @Query(
        """
        select new com.gmm.domain.review.repository.ReviewStats(
            r.gameId,
            count(r),
            avg((r.scoreFun + r.scoreTheme + r.scorePolish + r.scoreCreativity) / 4.0),
            avg(r.scoreFun * 1.0),
            avg(r.scoreTheme * 1.0),
            avg(r.scorePolish * 1.0),
            avg(r.scoreCreativity * 1.0)
        )
        from Review r
        where r.gameId in :gameIds
        group by r.gameId
        """,
    )
    fun findStats(gameIds: Collection<String>): List<ReviewStats>
}
