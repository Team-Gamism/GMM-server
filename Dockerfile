# Same base images as cowork-server (Temurin 27, Alpine 3.24), pinned by digest.
FROM eclipse-temurin:27.0.1.0-jdk-alpine-3.24@sha256:a63e42ef2a577cdb0802b73bb6f8fa2c3b0ebf1b4f00f29eb22e7f4d83b234f7 AS deps
WORKDIR /workspace
ENV GRADLE_USER_HOME=/root/.gradle
# Only build files here, so this layer (Gradle distribution and every dependency, plugins included)
# stays cached until a build file changes. CI stores it in the GitHub Actions cache (mode=max).
COPY gradlew settings.gradle.kts build.gradle.kts .editorconfig ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew resolveDependencies --no-daemon -q

FROM deps AS builder
COPY src src

# CI target: ktlint and unit tests. `docker build --target check .`
FROM builder AS check
RUN ./gradlew build --no-daemon

FROM builder AS package
RUN ./gradlew bootJar --no-daemon \
    && artifact="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && test -n "${artifact}" \
    && cp "${artifact}" /workspace/app.jar \
    && java -Djarmode=tools -jar /workspace/app.jar extract --layers --launcher --destination /workspace/extracted

FROM eclipse-temurin:27.0.1.0-jre-alpine-3.24@sha256:a0f3b523fd89e242ce302715ce367f68e9073c59274e65746d0791c750f38de3 AS runtime
RUN addgroup -S -g 10001 app && adduser -S -u 10001 app -G app
WORKDIR /app
COPY --chown=app:app --from=package /workspace/extracted/dependencies/ ./
COPY --chown=app:app --from=package /workspace/extracted/spring-boot-loader/ ./
COPY --chown=app:app --from=package /workspace/extracted/snapshot-dependencies/ ./
COPY --chown=app:app --from=package /workspace/extracted/application/ ./
# Builds and images live here. A new named volume copies this ownership, so the app can write to it.
ENV GMM_BUILD_ROOT_DIR=/data/gmm
RUN mkdir -p /data/gmm && chown app:app /data/gmm
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=60.0", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
