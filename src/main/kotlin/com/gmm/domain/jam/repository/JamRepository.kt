package com.gmm.domain.jam.repository

import com.gmm.domain.jam.entity.Jam
import org.springframework.data.jpa.repository.JpaRepository

interface JamRepository : JpaRepository<Jam, String>
