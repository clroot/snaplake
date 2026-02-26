package io.clroot.snaplake.adapter.inbound.web

import io.clroot.snaplake.adapter.inbound.web.dto.*
import io.clroot.snaplake.application.port.inbound.CompareDiffUseCase
import io.clroot.snaplake.application.port.inbound.CompareRowsUseCase
import io.clroot.snaplake.application.port.inbound.CompareSchemaUseCase
import io.clroot.snaplake.application.port.inbound.CompareStatsUseCase
import io.clroot.snaplake.application.port.inbound.CompareUnifiedDiffUseCase
import io.clroot.snaplake.domain.vo.SnapshotId
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/compare")
class CompareController(
    private val compareStatsUseCase: CompareStatsUseCase,
    private val compareRowsUseCase: CompareRowsUseCase,
    private val compareDiffUseCase: CompareDiffUseCase,
    private val compareUnifiedDiffUseCase: CompareUnifiedDiffUseCase,
    private val compareSchemaUseCase: CompareSchemaUseCase,
) {
    @PostMapping("/stats")
    fun compareStats(
        @RequestBody @Valid request: CompareStatsRequest,
    ): ResponseEntity<StatsResultResponse> {
        val result =
            compareStatsUseCase.compareStats(
                CompareStatsUseCase.Command(
                    leftSnapshotId = SnapshotId(request.leftSnapshotId),
                    rightSnapshotId = SnapshotId(request.rightSnapshotId),
                    tableName = request.tableName,
                ),
            )
        return ResponseEntity.ok(StatsResultResponse.from(result))
    }

    @PostMapping("/rows")
    fun compareRows(
        @RequestBody @Valid request: CompareRowsRequest,
    ): ResponseEntity<RowsCompareResultResponse> {
        val result =
            compareRowsUseCase.compareRows(
                CompareRowsUseCase.Command(
                    leftSnapshotId = SnapshotId(request.leftSnapshotId),
                    rightSnapshotId = SnapshotId(request.rightSnapshotId),
                    tableName = request.tableName,
                    limit = request.limit,
                    offset = request.offset,
                ),
            )
        return ResponseEntity.ok(RowsCompareResultResponse.from(result))
    }

    @PostMapping("/unified-diff")
    fun unifiedDiff(
        @RequestBody @Valid request: UnifiedDiffRequest,
    ): ResponseEntity<UnifiedDiffResponse> {
        val result =
            compareUnifiedDiffUseCase.compareUnifiedDiff(
                CompareUnifiedDiffUseCase.Command(
                    leftSnapshotId = SnapshotId(request.leftSnapshotId),
                    rightSnapshotId = SnapshotId(request.rightSnapshotId),
                    tableName = request.tableName,
                    limit = request.limit,
                    offset = request.offset,
                ),
            )
        return ResponseEntity.ok(UnifiedDiffResponse.from(result))
    }

    @PostMapping("/schema")
    fun compareSchema(
        @RequestBody @Valid request: CompareSchemaRequest,
    ): ResponseEntity<SchemaChangeResultResponse> {
        val result =
            compareSchemaUseCase.compareSchema(
                CompareSchemaUseCase.Command(
                    leftSnapshotId = SnapshotId(request.leftSnapshotId),
                    rightSnapshotId = SnapshotId(request.rightSnapshotId),
                ),
            )
        return ResponseEntity.ok(SchemaChangeResultResponse.from(result))
    }

    @PostMapping("/diff")
    fun compareDiff(
        @RequestBody @Valid request: CompareDiffRequest,
    ): ResponseEntity<QueryResultResponse> {
        val result =
            compareDiffUseCase.compareDiff(
                CompareDiffUseCase.Command(
                    leftSnapshotId = SnapshotId(request.leftSnapshotId),
                    rightSnapshotId = SnapshotId(request.rightSnapshotId),
                    tableName = request.tableName,
                    primaryKeys = request.primaryKeys,
                    limit = request.limit,
                    offset = request.offset,
                ),
            )
        return ResponseEntity.ok(QueryResultResponse.from(result))
    }
}
