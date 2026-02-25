package com.snaplake.adapter.inbound.web

import com.snaplake.adapter.inbound.web.dto.*
import com.snaplake.application.port.inbound.CompareDiffUseCase
import com.snaplake.application.port.inbound.CompareRowsUseCase
import com.snaplake.application.port.inbound.CompareStatsUseCase
import com.snaplake.application.port.inbound.CompareUnifiedDiffUseCase
import com.snaplake.domain.vo.SnapshotId
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
) {
    @PostMapping("/stats")
    fun compareStats(@RequestBody request: CompareStatsRequest): ResponseEntity<StatsResultResponse> {
        val result = compareStatsUseCase.compareStats(
            CompareStatsUseCase.Command(
                leftSnapshotId = SnapshotId(request.leftSnapshotId),
                rightSnapshotId = SnapshotId(request.rightSnapshotId),
                tableName = request.tableName,
            )
        )
        return ResponseEntity.ok(StatsResultResponse.from(result))
    }

    @PostMapping("/rows")
    fun compareRows(@RequestBody request: CompareRowsRequest): ResponseEntity<RowsCompareResultResponse> {
        val result = compareRowsUseCase.compareRows(
            CompareRowsUseCase.Command(
                leftSnapshotId = SnapshotId(request.leftSnapshotId),
                rightSnapshotId = SnapshotId(request.rightSnapshotId),
                tableName = request.tableName,
                limit = request.limit,
                offset = request.offset,
            )
        )
        return ResponseEntity.ok(RowsCompareResultResponse.from(result))
    }

    @PostMapping("/unified-diff")
    fun unifiedDiff(@RequestBody request: UnifiedDiffRequest): ResponseEntity<UnifiedDiffResponse> {
        val result = compareUnifiedDiffUseCase.compareUnifiedDiff(
            CompareUnifiedDiffUseCase.Command(
                leftSnapshotId = SnapshotId(request.leftSnapshotId),
                rightSnapshotId = SnapshotId(request.rightSnapshotId),
                tableName = request.tableName,
                limit = request.limit,
                offset = request.offset,
            )
        )
        return ResponseEntity.ok(UnifiedDiffResponse.from(result))
    }

    @PostMapping("/diff")
    fun compareDiff(@RequestBody request: CompareDiffRequest): ResponseEntity<QueryResultResponse> {
        val result = compareDiffUseCase.compareDiff(
            CompareDiffUseCase.Command(
                leftSnapshotId = SnapshotId(request.leftSnapshotId),
                rightSnapshotId = SnapshotId(request.rightSnapshotId),
                tableName = request.tableName,
                primaryKeys = request.primaryKeys,
                limit = request.limit,
                offset = request.offset,
            )
        )
        return ResponseEntity.ok(QueryResultResponse.from(result))
    }
}
