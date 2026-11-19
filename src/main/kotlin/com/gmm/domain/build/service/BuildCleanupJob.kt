package com.gmm.domain.build.service

import com.gmm.domain.build.storage.LocalDiskBuildStorage
import com.gmm.domain.game.repository.GameRepository
import com.gmm.global.config.GmmProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * File operations cannot roll back with the database. If the server stops between them, a build folder
 * can stay without a game row. This job removes such folders once a day.
 */
@Component
class BuildCleanupJob(
    private val storage: LocalDiskBuildStorage,
    private val gameRepository: GameRepository,
    private val properties: GmmProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    fun deleteOrphanBuilds() {
        val referenced = gameRepository.findAllBuildPaths().toHashSet()
        val threshold = clock.instant().minus(properties.build.cleanupGracePeriod)
        val deleted = storage.deleteOrphans(threshold) { it in referenced }
        if (deleted > 0) log.info("Delete {} orphan build directories", deleted)
    }
}
