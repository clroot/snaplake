package io.clroot.snaplake.adapter.inbound.web.dto

import io.clroot.snaplake.domain.model.Datasource
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant

data class RegisterDatasourceRequest(
    @field:NotBlank @field:Size(max = 100) val name: String,
    @field:NotBlank val type: String,
    @field:NotBlank val host: String,
    @field:Min(1) @field:Max(65535) val port: Int,
    @field:NotBlank val database: String,
    @field:NotBlank val username: String,
    @field:NotBlank val password: String,
    @field:NotEmpty val schemas: List<String>,
    val cronExpression: String?,
    @field:Min(0) val retentionDaily: Int = 0,
    @field:Min(0) val retentionMonthly: Int = 0,
    val includedTables: Map<String, List<String>> = emptyMap(),
)

data class UpdateDatasourceRequest(
    @field:NotBlank @field:Size(max = 100) val name: String,
    @field:NotBlank val type: String,
    @field:NotBlank val host: String,
    @field:Min(1) @field:Max(65535) val port: Int,
    @field:NotBlank val database: String,
    @field:NotBlank val username: String,
    val password: String?,
    @field:NotEmpty val schemas: List<String>,
    val cronExpression: String?,
    @field:Min(0) val retentionDaily: Int = 0,
    @field:Min(0) val retentionMonthly: Int = 0,
    val includedTables: Map<String, List<String>> = emptyMap(),
)

data class DatasourceResponse(
    val id: String,
    val name: String,
    val type: String,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val schemas: List<String>,
    val cronExpression: String?,
    val retentionDaily: Int,
    val retentionMonthly: Int,
    val includedTables: Map<String, List<String>>,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(ds: Datasource): DatasourceResponse =
            DatasourceResponse(
                id = ds.id.value,
                name = ds.name,
                type = ds.type.name,
                host = ds.host,
                port = ds.port,
                database = ds.database,
                username = ds.username,
                schemas = ds.schemas,
                cronExpression = ds.cronExpression,
                retentionDaily = ds.retentionPolicy.dailyMaxCount,
                retentionMonthly = ds.retentionPolicy.monthlyMaxCount,
                includedTables = ds.includedTables,
                enabled = ds.enabled,
                createdAt = ds.createdAt,
                updatedAt = ds.updatedAt,
            )
    }
}

data class ConnectionTestResponse(
    val success: Boolean,
    val message: String,
)
