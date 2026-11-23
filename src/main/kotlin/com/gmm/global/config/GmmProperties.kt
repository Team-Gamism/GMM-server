package com.gmm.global.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize
import java.nio.file.Path
import java.time.Duration

@ConfigurationProperties("gmm")
data class GmmProperties(
    val cors: Cors = Cors(),
    val session: Session = Session(),
    val admin: Admin = Admin(),
    val datagsm: DataGsm = DataGsm(),
    val build: Build = Build(),
    val image: Image = Image(),
) {
    data class Cors(val allowedOrigins: List<String> = emptyList())

    data class Session(
        val cookieName: String = "GMM_SESSION",
        val cookieSecure: Boolean = true,
        val ttl: Duration = Duration.ofDays(14),
    )

    data class Admin(val emails: List<String> = emptyList())

    data class DataGsm(
        val clientId: String = "",
        val tokenUri: String = "https://oauth.authorization.datagsm.kr/v1/oauth/token",
        val userInfoUri: String = "https://oauth.resource.datagsm.kr/userinfo",
        val allowedRedirectUris: List<String> = emptyList(),
        val allowTeacher: Boolean = false,
        val connectTimeout: Duration = Duration.ofSeconds(5),
        val readTimeout: Duration = Duration.ofSeconds(10),
    )

    data class Image(
        /** Upper limit for one image. The frontend already resizes icons, covers and avatars. */
        val maxSize: DataSize = DataSize.ofMegabytes(5),
    )

    data class Build(
        /** Holds `builds/` (served by nginx) and `staging/` (work area, never served). */
        val rootDir: Path = Path.of("./data"),
        /** Origin that serves `builds/`, without a trailing slash. */
        val publicBaseUrl: String = "http://localhost:8081",
        val maxZipSize: DataSize = DataSize.ofMegabytes(200),
        val maxExtractedSize: DataSize = DataSize.ofMegabytes(500),
        val maxFileCount: Int = 2000,
        /** Cleanup leaves files younger than this alone, so it never races an upload in progress. */
        val cleanupGracePeriod: Duration = Duration.ofHours(6),
    )
}
