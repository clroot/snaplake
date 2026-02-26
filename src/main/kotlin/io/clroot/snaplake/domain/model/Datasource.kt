package io.clroot.snaplake.domain.model

import io.clroot.snaplake.domain.vo.DatasourceId
import java.time.Instant

enum class DatabaseType { POSTGRESQL, MYSQL }

data class RetentionPolicy(
    val dailyMaxCount: Int = 0,
    val monthlyMaxCount: Int = 0,
) {
    init {
        require(dailyMaxCount >= 0) { "dailyMaxCount must be non-negative" }
        require(monthlyMaxCount >= 0) { "monthlyMaxCount must be non-negative" }
    }
}

class Datasource private constructor(
    val id: DatasourceId,
    val name: String,
    val type: DatabaseType,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val encryptedPassword: String,
    val schemas: List<String>,
    val cronExpression: String?,
    val retentionPolicy: RetentionPolicy,
    val includedTables: Map<String, List<String>>,
    val createdAt: Instant,
    updatedAt: Instant,
    enabled: Boolean,
) {
    var enabled: Boolean = enabled
        private set

    var updatedAt: Instant = updatedAt
        private set

    fun disable() {
        enabled = false
        updatedAt = Instant.now()
    }

    fun enable() {
        enabled = true
        updatedAt = Instant.now()
    }

    companion object {
        fun create(
            name: String,
            type: DatabaseType,
            host: String,
            port: Int,
            database: String,
            username: String,
            encryptedPassword: String,
            schemas: List<String>,
            cronExpression: String?,
            retentionPolicy: RetentionPolicy = RetentionPolicy(),
            includedTables: Map<String, List<String>> = emptyMap(),
        ): Datasource {
            require(name.isNotBlank()) { "Datasource name must not be blank" }
            require(!name.contains("..") && !name.contains('/') && !name.contains('\\')) {
                "Datasource name must not contain path characters (/, \\, ..)"
            }
            require(host.isNotBlank()) { "Host must not be blank" }
            require(port in 1..65535) { "Port must be between 1 and 65535" }
            require(database.isNotBlank()) { "Database name must not be blank" }
            require(username.isNotBlank()) { "Username must not be blank" }
            require(schemas.isNotEmpty()) { "At least one schema is required" }

            val now = Instant.now()
            return Datasource(
                id = DatasourceId.generate(),
                name = name.trim(),
                type = type,
                host = host.trim(),
                port = port,
                database = database.trim(),
                username = username.trim(),
                encryptedPassword = encryptedPassword,
                schemas = schemas,
                cronExpression = cronExpression,
                retentionPolicy = retentionPolicy,
                includedTables = includedTables,
                enabled = true,
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: DatasourceId,
            name: String,
            type: DatabaseType,
            host: String,
            port: Int,
            database: String,
            username: String,
            encryptedPassword: String,
            schemas: List<String>,
            cronExpression: String?,
            retentionPolicy: RetentionPolicy,
            includedTables: Map<String, List<String>> = emptyMap(),
            enabled: Boolean,
            createdAt: Instant,
            updatedAt: Instant,
        ): Datasource =
            Datasource(
                id,
                name,
                type,
                host,
                port,
                database,
                username,
                encryptedPassword,
                schemas,
                cronExpression,
                retentionPolicy,
                includedTables,
                createdAt,
                updatedAt,
                enabled,
            )
    }
}
