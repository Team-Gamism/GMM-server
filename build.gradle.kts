plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.ktlint)
}

group = "com.gmm"
version = "0.0.1"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

ktlint {
    version.set(libs.versions.ktlint.asProvider())
}

// Keep ktlint's compiler dependencies independent of the Spring BOM.
configurations.matching { it.name.startsWith("ktlint") }.configureEach {
    resolutionStrategy.eachDependency {
        val requestedVersion = requested.version
        if (requested.group == "org.jetbrains.kotlin" && !requestedVersion.isNullOrBlank()) {
            useVersion(requestedVersion)
            because("Keep ktlint's compiler dependencies independent of the Spring BOM")
        }
    }
}

dependencies {
    // Spring APIs require UnknownNullability from the pinned annotations version.
    compileOnly(libs.jetbrains.annotations)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.mysql.connector.j)
    implementation(libs.spring.boot.flyway)
    implementation(libs.flyway.core)
    implementation(libs.flyway.mysql)
    implementation(libs.kotlin.reflect)
    implementation(libs.springdoc.openapi.webmvc.ui)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.named("jar") {
    enabled = false
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Hibernate needs open entity classes for lazy proxies.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

// Downloads every resolvable dependency (compile, test, Kotlin compiler, ktlint) so the Docker
// build can keep them in one cached layer. See the `deps` stage in the Dockerfile.
tasks.register("resolveDependencies") {
    description = "Downloads all dependencies for an offline-capable build cache."
    doLast {
        configurations
            .filter { it.isCanBeResolved }
            .forEach { configuration -> runCatching { configuration.files } }
    }
}
