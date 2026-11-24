package com.gmm.domain.jam.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** An award announced for a jam. `rank` is the overall rank at announcement time. */
@Entity
@Table(name = "tb_jam_award")
class JamAward(
    @Column(name = "jam_id", nullable = false)
    val jamId: String,
    @Column(name = "game_id", nullable = false)
    val gameId: String,
    @Column(name = "award_rank", nullable = false)
    val rank: Int,
    @Column(nullable = false)
    val title: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
