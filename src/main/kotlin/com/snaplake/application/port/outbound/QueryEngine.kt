package com.snaplake.application.port.outbound

import com.snaplake.domain.model.StorageConfig

data class QueryResult(
    val columns: List<ColumnSchema>,
    val rows: List<List<Any?>>,
    val totalRows: Long,
)

data class ColumnSchema(
    val name: String,
    val type: String,
)

interface QueryEngine {
    fun executeQuery(
        sql: String,
        storageConfig: StorageConfig?,
        limit: Int = 100,
        offset: Int = 0,
        viewSetupSql: List<String> = emptyList(),
    ): QueryResult
    fun describeTable(uri: String, storageConfig: StorageConfig?): List<ColumnSchema>
    fun countRows(uri: String, storageConfig: StorageConfig?): Long
    fun previewTable(
        uri: String,
        storageConfig: StorageConfig?,
        where: String? = null,
        orderBy: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): QueryResult
}
