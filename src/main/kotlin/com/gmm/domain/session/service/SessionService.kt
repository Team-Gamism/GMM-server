package com.gmm.domain.session.service

import com.gmm.domain.session.entity.UserSession
import com.gmm.domain.session.repository.UserSessionRepository
import com.gmm.domain.user.repository.UserRepository
import com.gmm.domain.user.service.AdminPolicy
import com.gmm.global.auth.AuthUser
import com.gmm.global.config.GmmProperties
import com.gmm.global.support.nowMillis
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.HexFormat

@Service
class SessionService(
    private val sessionRepository: UserSessionRepository,
    private val userRepository: UserRepository,
    private val adminPolicy: AdminPolicy,
    private val properties: GmmProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    /** Creates a session and returns the raw cookie value. Only its hash is stored. */
    @Transactional
    fun create(userId: String): String {
        val token = newToken()
        val now = clock.nowMillis()
        sessionRepository.save(
            UserSession(
                id = hash(token),
                userId = userId,
                createdAt = now,
                expiresAt = now.plus(properties.session.ttl),
            ),
        )
        return token
    }

    /**
     * Resolves the session and reads the role on every request, so a granted or revoked admin takes
     * effect right away. It writes only when an owner's stored role is not admin yet.
     *
     * Not transactional on purpose: it runs on every request, and its two primary-key reads each use the
     * repository's own read-only transaction instead of one read-write transaction per request.
     */
    fun authenticate(token: String): AuthUser? {
        if (token.isBlank() || token.length > MAX_TOKEN_LENGTH) return null
        val session = sessionRepository.findById(hash(token)).orElse(null) ?: return null
        if (session.isExpiredAt(clock.instant())) return null
        val user = userRepository.findById(session.userId).orElse(null) ?: return null
        val role = adminPolicy.roleFor(user.email, user.role)
        if (role != user.role) {
            userRepository.updateRole(user.id, role, clock.nowMillis())
            log.info("Change role of user {} to {} from the owner list", user.id, role)
        }
        return AuthUser(user.id, role)
    }

    @Transactional
    fun revoke(token: String) {
        if (token.isBlank() || token.length > MAX_TOKEN_LENGTH) return
        sessionRepository.deleteById(hash(token))
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @Transactional
    fun deleteExpired() {
        val deleted = sessionRepository.deleteExpired(clock.instant())
        if (deleted > 0) log.info("Delete {} expired sessions", deleted)
    }

    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    companion object {
        private const val TOKEN_BYTES = 32
        private const val MAX_TOKEN_LENGTH = 128
    }
}
