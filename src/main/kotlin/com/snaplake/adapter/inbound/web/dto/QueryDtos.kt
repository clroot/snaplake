package com.snaplake.adapter.inbound.web.dto

import com.snaplake.application.port.outbound.ColumnSchema
import com.snaplake.application.port.outbound.QueryResult

data class ExecuteQueryRequest(
    val sql: String,
    val limit: Int = 100,
    val offset: Int = 0,
)

data class QueryResultResponse(
    val columns: List<ColumnSchemaResponse>,
    val rows: List<List<Any?>>,
    val totalRows: Long,
) {
    companion object {
        fun from(result: QueryResult): QueryResultResponse = QueryResultResponse(
            columns = result.columns.map { ColumnSchemaResponse.from(it) },
            rows = result.rows,
            totalRows = result.totalRows,
        )
    }
}

data class ColumnSchemaResponse(
    val name: String,
    val type: String,
) {
    companion object {
        fun from(schema: ColumnSchema): ColumnSchemaResponse = ColumnSchemaResponse(
            name = schema.name,
            type = schema.type,
        )
    }
}
