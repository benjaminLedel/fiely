# syntax=docker/dockerfile:1.7
#
# Fiely — all-in-one container.
#
# Stages:
#   1. frontend — builds fiely-frontend/ with Node → dist/
#   2. backend  — builds fiely-backend/ with Gradle, embedding the frontend
#                 dist as Spring Boot static resources, producing the fat JAR
#   3. runtime  — slim JRE image running the fat JAR

# --- 1. Frontend ---
FROM node:22-alpine AS frontend
WORKDIR /workspace/fiely-frontend

# Install deps first for better layer caching
COPY fiely-frontend/package.json fiely-frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

# Build
COPY fiely-frontend/ ./
RUN npm run build


# --- 2. Backend ---
FROM gradle:8.11-jdk21 AS backend
WORKDIR /workspace/fiely-backend

# Build scripts first so dependency resolution can be cached
COPY --chown=gradle:gradle fiely-backend/settings.gradle.kts fiely-backend/build.gradle.kts ./
COPY --chown=gradle:gradle fiely-backend/gradle ./gradle
COPY --chown=gradle:gradle fiely-backend/gradlew fiely-backend/gradlew.bat ./

# Submodule build scripts
COPY --chown=gradle:gradle fiely-backend/fiely-plugin-api/build.gradle.kts fiely-plugin-api/build.gradle.kts
COPY --chown=gradle:gradle fiely-backend/fiely-core/build.gradle.kts fiely-core/build.gradle.kts
COPY --chown=gradle:gradle fiely-backend/plugins plugins

# Warm the dependency cache (best effort — ignore failures from stub modules)
RUN ./gradlew :fiely-core:dependencies --no-daemon > /dev/null || true

# Sources
COPY --chown=gradle:gradle fiely-backend/fiely-plugin-api/src fiely-plugin-api/src
COPY --chown=gradle:gradle fiely-backend/fiely-core/src fiely-core/src

# Embed the built frontend as Spring Boot static resources.
# Anything under src/main/resources/static/ is served from the root URL by
# default. The SpaWebConfig also handles the SPA fallback for deep links.
COPY --from=frontend --chown=gradle:gradle \
    /workspace/fiely-frontend/dist/ \
    fiely-core/src/main/resources/static/

# Build the executable fat JAR
RUN ./gradlew :fiely-core:bootJar --no-daemon -x test


# --- 3. Runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl is used for the HEALTHCHECK
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Non-root user
RUN groupadd --system --gid 1001 fiely \
    && useradd --system --uid 1001 --gid fiely --home /app --shell /sbin/nologin fiely

COPY --from=backend --chown=fiely:fiely \
    /workspace/fiely-backend/fiely-core/build/libs/fiely-core-*.jar \
    /app/fiely-core.jar

USER fiely

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/fiely-core.jar"]
