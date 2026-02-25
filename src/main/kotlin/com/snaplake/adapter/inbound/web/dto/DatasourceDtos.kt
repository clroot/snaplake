package com.snaplake.adapter.inbound.web.dto

import com.snaplake.domain.model.Datasource
import java.time.Instant

data class RegisterDatasourceRequest(
    val name: String,
    val type: String,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val schemas: List<String>,
    val cronExpression: String?,
    val retentionDaily: Int = 0,
    val retentionMonthly: Int = 0,
)

data class UpdateDatasourceRequest(
    val name: String,
    val type: String,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String?,
    val schemas: List<String>,
    val cronExpression: String?,
    val retentionDaily: Int = 0,
    val retentionMonthly: Int = 0,
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
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(ds: Datasource): DatasourceResponse = DatasourceResponse(
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
