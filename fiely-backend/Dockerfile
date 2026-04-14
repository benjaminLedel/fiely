# syntax=docker/dockerfile:1.7

# --- Build stage ---
FROM gradle:8.11-jdk21 AS build
WORKDIR /workspace

# Copy Gradle wrapper and build scripts first for better layer caching
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts ./
COPY --chown=gradle:gradle gradle ./gradle
COPY --chown=gradle:gradle gradlew gradlew.bat ./

# Copy submodule build scripts
COPY --chown=gradle:gradle fiely-plugin-api/build.gradle.kts fiely-plugin-api/build.gradle.kts
COPY --chown=gradle:gradle fiely-core/build.gradle.kts fiely-core/build.gradle.kts
COPY --chown=gradle:gradle plugins plugins

# Warm the dependency cache (best effort — ignore failures from stub modules)
RUN ./gradlew :fiely-core:dependencies --no-daemon > /dev/null || true

# Copy all sources and build the executable JAR
COPY --chown=gradle:gradle fiely-plugin-api/src fiely-plugin-api/src
COPY --chown=gradle:gradle fiely-core/src fiely-core/src

RUN ./gradlew :fiely-core:bootJar --no-daemon -x test

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl is used for the HEALTHCHECK
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd --system --gid 1001 fiely \
    && useradd --system --uid 1001 --gid fiely --home /app --shell /sbin/nologin fiely

COPY --from=build --chown=fiely:fiely /workspace/fiely-core/build/libs/fiely-core-*.jar /app/fiely-core.jar

USER fiely

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/fiely-core.jar"]
