package com.gmm.domain.game.entity

import com.gmm.global.auth.AuthUser
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "tb_game")
class Game(
    @Id
    val id: String,
    @Column(name = "jam_id", nullable = false)
    val jamId: String,
    @Column(name = "owner_id", nullable = false)
    val ownerId: String,
    details: GameDetails,
    build: GameBuild,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    @Column(nullable = false, length = 40)
    var title: String = details.title
        protected set

    @Column(nullable = false, length = 80)
    var tagline: String = details.tagline
        protected set

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = details.description
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var genre: Genre = details.genre
        protected set

    @Column(name = "icon_url")
    var iconUrl: String? = details.iconUrl
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var orientation: Orientation = details.orientation
        protected set

    @Convert(converter = PlatformListConverter::class)
    @Column(name = "env_platforms", nullable = false)
    var envPlatforms: List<Platform> = details.platforms
        protected set

    @Convert(converter = InputDeviceListConverter::class)
    @Column(name = "env_inputs", nullable = false)
    var envInputs: List<InputDevice> = details.inputs
        protected set

    @Column(name = "env_note", nullable = false)
    var envNote: String = details.note
        protected set

    /** `{gameId}/{uploadId}` relative to the build storage root. */
    @Column(name = "build_path")
    var buildPath: String? = build.path
        protected set

    @Column(name = "build_name")
    var buildName: String? = build.name
        protected set

    @Column(name = "build_file_name")
    var buildFileName: String? = build.fileName
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    var reviewStatus: ReviewStatus = ReviewStatus.PENDING
        protected set

    @Column(name = "reject_reason")
    var rejectReason: String? = null
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    fun updateDetails(details: GameDetails, now: Instant) {
        title = details.title
        tagline = details.tagline
        description = details.description
        genre = details.genre
        iconUrl = details.iconUrl
        orientation = details.orientation
        envPlatforms = details.platforms
        envInputs = details.inputs
        envNote = details.note
        updatedAt = now
    }

    /** A new build always needs a fresh upload review. Returns the replaced build path. */
    fun replaceBuild(build: GameBuild, now: Instant): String? {
        val previous = buildPath
        buildPath = build.path
        buildName = build.name
        buildFileName = build.fileName
        reviewStatus = ReviewStatus.PENDING
        rejectReason = null
        updatedAt = now
        return previous
    }

    fun isOwnedBy(user: AuthUser): Boolean = ownerId == user.id

    fun canManage(user: AuthUser): Boolean = user.isAdmin || isOwnedBy(user)

    /** Approved games are public. Pending and rejected games are visible to the owner and admins only. */
    fun isVisibleTo(user: AuthUser?): Boolean =
        reviewStatus == ReviewStatus.APPROVED || (user != null && canManage(user))
}

data class GameDetails(
    val title: String,
    val tagline: String,
    val description: String,
    val genre: Genre,
    val iconUrl: String?,
    val orientation: Orientation,
    val platforms: List<Platform>,
    val inputs: List<InputDevice>,
    val note: String,
)

data class GameBuild(val path: String, val name: String, val fileName: String)
