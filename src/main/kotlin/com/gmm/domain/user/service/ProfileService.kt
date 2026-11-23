package com.gmm.domain.user.service

import com.gmm.domain.game.entity.ReviewStatus
import com.gmm.domain.game.repository.GameRepository
import com.gmm.domain.game.service.GameSummaries
import com.gmm.domain.jam.entity.JamAward
import com.gmm.domain.jam.repository.JamAwardRepository
import com.gmm.domain.jam.repository.JamRepository
import com.gmm.domain.user.presentation.ProfileAwardResponse
import com.gmm.domain.user.presentation.UserProfileResponse
import com.gmm.domain.user.presentation.UserResponse
import com.gmm.domain.user.repository.UserRepository
import com.gmm.global.auth.AuthUser
import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import com.gmm.global.support.nowMillis
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class ProfileService(
    private val userRepository: UserRepository,
    private val gameRepository: GameRepository,
    private val jamRepository: JamRepository,
    private val jamAwardRepository: JamAwardRepository,
    private val gameSummaries: GameSummaries,
    private val clock: Clock,
) {
    /** Only approved games and awards of announced jams count, as in the frontend mock. */
    @Transactional(readOnly = true)
    fun profile(userId: String): UserProfileResponse {
        val user = userRepository.findById(userId).orElseThrow { GmmException(ErrorCode.USER_NOT_FOUND) }
        val games = gameRepository.findAllByOwnerIdAndReviewStatusOrderByCreatedAtDesc(user.id, ReviewStatus.APPROVED)
        val titles = games.associate { it.id to it.title }

        val awards = jamAwardRepository.findAllByGameIdIn(titles.keys)
        val announcedJams =
            jamRepository.findAllById(
                awards.map {
                    it.jamId
                }.toSet(),
            ).filter { it.announcedAt != null }.associateBy { it.id }
        val profileAwards = awards
            .filter { it.jamId in announcedJams }
            .sortedWith(compareByDescending<JamAward> { announcedJams.getValue(it.jamId).startAt }.thenBy { it.rank })
            .map {
                ProfileAwardResponse(
                    gameId = it.gameId,
                    rank = it.rank,
                    title = it.title,
                    jamId = it.jamId,
                    jamName = announcedJams.getValue(it.jamId).name,
                    gameTitle = titles.getValue(it.gameId),
                )
            }

        return UserProfileResponse(
            id = user.id,
            name = user.name,
            generation = user.generation,
            avatarUrl = user.avatarUrl,
            role = user.role,
            entryCount = games.size,
            winCount = profileAwards.count { it.rank == 1 },
            games = gameSummaries.summarize(games),
            awards = profileAwards,
        )
    }

    @Transactional
    fun changeAvatar(user: AuthUser, avatarUrl: String?): UserResponse {
        val entity = userRepository.findById(user.id).orElseThrow { GmmException(ErrorCode.UNAUTHORIZED) }
        entity.changeAvatar(avatarUrl, clock.nowMillis())
        return UserResponse.from(entity)
    }
}
