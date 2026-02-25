# Stage 1: Frontend build
FROM oven/bun:latest AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/bun.lockb* ./
RUN bun install --frozen-lockfile
COPY frontend/ .
RUN bun run build

# Stage 2: Backend build
FROM gradle:8.13-jdk21 AS backend
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src/ src/
COPY --from=frontend /app/frontend/../src/main/resources/static src/main/resources/static
RUN gradle bootJar --no-daemon -x buildFrontend -x test

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S snaplake && adduser -S snaplake -G snaplake
RUN mkdir -p /app/data && chown -R snaplake:snaplake /app

COPY --from=backend --chown=snaplake:snaplake /app/build/libs/*.jar snaplake.jar

USER snaplake

EXPOSE 8080
VOLUME /app/data

ENV SNAPLAKE_DATA_DIR=/app/data

ENTRYPOINT ["java", "-jar", "snaplake.jar"]
