# Stage 1: Frontend build
FROM oven/bun:latest AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/bun.lock ./
RUN bun install --frozen-lockfile
COPY frontend/ .
RUN bun run build

# Stage 2: Backend build
FROM gradle:9.1.0-jdk25 AS backend
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src/ src/
COPY --from=frontend /app/frontend/../src/main/resources/static src/main/resources/static
RUN gradle bootJar --no-daemon -x buildFrontend -x test

# Stage 3: Runtime
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd -r snaplake && useradd -r -g snaplake snaplake
RUN mkdir -p /app/data && chown -R snaplake:snaplake /app

COPY --from=backend --chown=snaplake:snaplake /app/build/libs/*.jar snaplake.jar

USER snaplake

EXPOSE 8080
VOLUME /app/data

ENV SNAPLAKE_DATA_DIR=/app/data

ENTRYPOINT ["java", "-jar", "snaplake.jar"]
