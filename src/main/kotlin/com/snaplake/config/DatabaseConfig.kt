package com.snaplake.config

import liquibase.integration.spring.SpringLiquibase
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

@Configuration
@EnableConfigurationProperties(LiquibaseProperties::class)
class DatabaseConfig(
    @Value("\${snaplake.data-dir}") private val dataDir: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun liquibase(dataSource: DataSource, properties: LiquibaseProperties): SpringLiquibase {
        ensureDataDirectory()

        return SpringLiquibase().apply {
            this.dataSource = dataSource
            changeLog = properties.changeLog
            setShouldRun(properties.isEnabled)
        }
    }

    private fun ensureDataDirectory() {
        val path = Path.of(dataDir)
        if (!Files.exists(path)) {
            Files.createDirectories(path)
            log.info("Created data directory: {}", path.toAbsolutePath())
        }
    }
}
