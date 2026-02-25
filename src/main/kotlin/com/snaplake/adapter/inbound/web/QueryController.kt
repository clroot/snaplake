package com.snaplake.adapter.inbound.web

import com.snaplake.adapter.inbound.web.dto.ColumnSchemaResponse
import com.snaplake.adapter.inbound.web.dto.ExecuteQueryRequest
import com.snaplake.adapter.inbound.web.dto.QueryResultResponse
import com.snaplake.adapter.inbound.web.dto.SnapshotSchemaResponse
import com.snaplake.application.port.inbound.DescribeTableUseCase
import com.snaplake.application.port.inbound.ExecuteQueryUseCase
import com.snaplake.application.port.inbound.PreviewTableUseCase
import com.snaplake.domain.vo.SnapshotId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class QueryController(
    private val executeQueryUseCase: ExecuteQueryUseCase,
    private val describeTableUseCase: DescribeTableUseCase,
    private val previewTableUseCase: PreviewTableUseCase,
) {
    @PostMapping("/query")
    fun executeQuery(@RequestBody request: ExecuteQueryRequest): ResponseEntity<QueryResultResponse> {
        val context = request.context?.let { ctx ->
            ExecuteQueryUseCase.SnapshotContext(
                snapshots = ctx.snapshots.map { entry ->
                    ExecuteQueryUseCase.AliasedSnapshot(
                        snapshotId = SnapshotId(entry.snapshotId),
                        alias = entry.alias,
                    )
                },
            )
        }

        val result = executeQueryUseCase.executeQuery(
            ExecuteQueryUseCase.Command(
                sql = request.sql,
                limit = request.limit,
                offset = request.offset,
                context = context,
            )
        )
        return ResponseEntity.ok(QueryResultResponse.from(result))
    }

    @GetMapping("/snapshots/{snapshotId}/schema")
    fun describeAllTables(
        @PathVariable snapshotId: String,
    ): ResponseEntity<SnapshotSchemaResponse> {
        val schema = describeTableUseCase.describeAll(SnapshotId(snapshotId))
        return ResponseEntity.ok(SnapshotSchemaResponse.from(schema))
    }

    @GetMapping("/snapshots/{snapshotId}/tables/{tableName}/schema")
    fun describeTable(
        @PathVariable snapshotId: String,
        @PathVariable tableName: String,
    ): ResponseEntity<List<ColumnSchemaResponse>> {
        val columns = describeTableUseCase.describe(SnapshotId(snapshotId), tableName)
        return ResponseEntity.ok(columns.map { ColumnSchemaResponse.from(it) })
    }

    @GetMapping("/snapshots/{snapshotId}/tables/{tableName}/preview")
    fun previewTable(
        @PathVariable snapshotId: String,
        @PathVariable tableName: String,
        @RequestParam(required = false) where: String?,
        @RequestParam(required = false) orderBy: String?,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): ResponseEntity<QueryResultResponse> {
        val result = previewTableUseCase.preview(
            PreviewTableUseCase.Command(
                snapshotId = SnapshotId(snapshotId),
                tableName = tableName,
                where = where,
                orderBy = orderBy,
                limit = limit,
                offset = offset,
            )
        )
        return ResponseEntity.ok(QueryResultResponse.from(result))
    }
}
