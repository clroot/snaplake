plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.spring") version "2.1.10"
    kotlin("plugin.jpa") version "2.1.10"
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.clroot.snaplake"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // SQLite
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
    implementation("org.hibernate.orm:hibernate-community-dialects")

    // Liquibase
    implementation("org.liquibase:liquibase-core")

    // DuckDB
    implementation("org.duckdb:duckdb_jdbc:1.2.1")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // S3
    implementation("software.amazon.awssdk:s3:2.30.18")

    // PostgreSQL + MySQL JDBC
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.mysql:mysql-connector-j")

    // Encryption
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.kotest:kotest-runner-junit5:6.1.3")
    testImplementation("io.kotest:kotest-assertions-core:6.1.3")
    testImplementation("io.kotest:kotest-extensions-spring:6.1.3")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("com.tngtech.archunit:archunit:1.4.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val bunExecutable: String by lazy {
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val bunBinary = if (isWindows) "bun.exe" else "bun"

    // 1) BUN_INSTALL env (custom install dir)  2) default ~/.bun
    listOfNotNull(
        System.getenv("BUN_INSTALL")?.let { File(it, "bin/$bunBinary") },
        File(System.getProperty("user.home"), ".bun/bin/$bunBinary"),
    )
        .firstOrNull { it.exists() }
        ?.absolutePath
    // 3) Resolve from PATH via which/where
        ?: runCatching {
            val cmd = if (isWindows) listOf("where", "bun") else listOf("which", "bun")
            ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader().readLine()?.trim()
                ?.takeIf { File(it).exists() }
        }.getOrNull()
    // 4) Fallback — let OS resolve
        ?: "bun"
}

tasks.register<Exec>("installFrontend") {
    workingDir = file("frontend")
    commandLine(bunExecutable, "install", "--frozen-lockfile")
    inputs.files("frontend/package.json", "frontend/bun.lock")
    outputs.dir("frontend/node_modules")
}

tasks.register<Exec>("buildFrontend") {
    dependsOn("installFrontend")
    workingDir = file("frontend")
    commandLine(bunExecutable, "run", "build")
    inputs.dir("frontend/src")
    inputs.files(
        "frontend/index.html",
        "frontend/vite.config.ts",
        "frontend/tsconfig.json",
        "frontend/tsconfig.app.json",
    )
    outputs.dir("src/main/resources/static")
}

tasks.named("processResources") {
    dependsOn("buildFrontend")
}
