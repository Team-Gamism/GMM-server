package com.gmm.domain.game.entity

import com.gmm.domain.jam.entity.JamStatus
import com.gmm.global.auth.AuthUser
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
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

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    /**
     * Optimistic lock. A metadata edit and a build replacement can overlap; without it the
     * last commit would write back a stale build path. Null until persisted, so Spring Data treats a new game as new.
     */
    @Version
    @Column(nullable = false)
    var version: Long? = null
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

    /** Returns the replaced build path. */
    fun replaceBuild(build: GameBuild, now: Instant): String? {
        val previous = buildPath
        buildPath = build.path
        buildName = build.name
        buildFileName = build.fileName
        updatedAt = now
        return previous
    }

    fun isOwnedBy(user: AuthUser): Boolean = ownerId == user.id

    fun canManage(user: AuthUser): Boolean = user.isAdmin || isOwnedBy(user)

    /** Games are public once the jam's submissions close. Until then only the owner and admins see them. */
    fun isVisibleTo(user: AuthUser?, jamStatus: JamStatus): Boolean =
        jamStatus.isPublic || (user != null && canManage(user))
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

data class GameBuild(val path: String, val name: String?, val fileName: String)
