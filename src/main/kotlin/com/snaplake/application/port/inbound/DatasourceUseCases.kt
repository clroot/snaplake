package com.snaplake.application.port.inbound

import com.snaplake.application.port.outbound.ConnectionTestResult
import com.snaplake.domain.model.DatabaseType
import com.snaplake.domain.model.Datasource
import com.snaplake.domain.model.RetentionPolicy
import com.snaplake.domain.vo.DatasourceId

interface RegisterDatasourceUseCase {
    fun register(command: Command): Datasource

    data class Command(
        val name: String,
        val type: DatabaseType,
        val host: String,
        val port: Int,
        val database: String,
        val username: String,
        val password: String,
        val schemas: List<String>,
        val cronExpression: String?,
        val retentionPolicy: RetentionPolicy,
    )
}

interface UpdateDatasourceUseCase {
    fun update(id: DatasourceId, command: Command): Datasource

    data class Command(
        val name: String,
        val type: DatabaseType,
        val host: String,
        val port: Int,
        val database: String,
        val username: String,
        val password: String?,
        val schemas: List<String>,
        val cronExpression: String?,
        val retentionPolicy: RetentionPolicy,
    )
}

interface DeleteDatasourceUseCase {
    fun delete(id: DatasourceId)
}

interface GetDatasourceUseCase {
    fun getById(id: DatasourceId): Datasource
    fun getAll(): List<Datasource>
}

interface TestDatasourceConnectionUseCase {
    fun test(id: DatasourceId): ConnectionTestResult
}
