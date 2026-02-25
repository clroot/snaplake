package com.snaplake.adapter.inbound.web.dto

import com.snaplake.application.port.inbound.*

data class CompareStatsRequest(
    val leftSnapshotId: String,
    val rightSnapshotId: String,
    val tableName: String,
)

data class CompareRowsRequest(
    val leftSnapshotId: String,
    val rightSnapshotId: String,
    val tableName: String,
    val limit: Int = 100,
    val offset: Int = 0,
)

data class CompareDiffRequest(
    val leftSnapshotId: String,
    val rightSnapshotId: String,
    val tableName: String,
    val primaryKeys: List<String>,
    val limit: Int = 100,
    val offset: Int = 0,
)

data class StatsResultResponse(
    val leftRowCount: Long,
    val rightRowCount: Long,
    val columnStats: List<ColumnStatResponse>,
) {
    companion object {
        fun from(result: StatsResult): StatsResultResponse = StatsResultResponse(
            leftRowCount = result.leftRowCount,
            rightRowCount = result.rightRowCount,
            columnStats = result.columnStats.map { ColumnStatResponse.from(it) },
        )
    }
}

data class ColumnStatResponse(
    val column: String,
    val leftDistinctCount: Long,
    val rightDistinctCount: Long,
    val leftNullCount: Long,
    val rightNullCount: Long,
) {
    companion object {
        fun from(stat: ColumnStat): ColumnStatResponse = ColumnStatResponse(
            column = stat.column,
            leftDistinctCount = stat.leftDistinctCount,
            rightDistinctCount = stat.rightDistinctCount,
            leftNullCount = stat.leftNullCount,
            rightNullCount = stat.rightNullCount,
        )
    }
}

data class RowsCompareResultResponse(
    val added: QueryResultResponse,
    val removed: QueryResultResponse,
) {
    companion object {
        fun from(result: RowsCompareResult): RowsCompareResultResponse = RowsCompareResultResponse(
            added = QueryResultResponse.from(result.added),
            removed = QueryResultResponse.from(result.removed),
        )
    }
}

data class UnifiedDiffRequest(
    val leftSnapshotId: String,
    val rightSnapshotId: String,
    val tableName: String,
    val limit: Int = 100,
    val offset: Int = 0,
)

data class DiffSummaryResponse(val added: Long, val removed: Long, val changed: Long)

data class DiffRowResponse(
    val diffType: String,
    val values: List<Any?>? = null,
    val left: List<Any?>? = null,
    val right: List<Any?>? = null,
    val changedColumns: List<Int>? = null,
)

data class UnifiedDiffResponse(
    val columns: List<ColumnResponse>,
    val primaryKeys: List<String>,
    val rows: List<DiffRowResponse>,
    val totalRows: Long,
    val summary: DiffSummaryResponse,
) {
    companion object {
        fun from(result: UnifiedDiffResult): UnifiedDiffResponse = UnifiedDiffResponse(
            columns = result.columns.map { ColumnResponse(it.name, it.type) },
            primaryKeys = result.primaryKeys,
            rows = result.rows.map { row ->
                when (row) {
                    is DiffRow.Added -> DiffRowResponse(diffType = "ADDED", values = row.values)
                    is DiffRow.Removed -> DiffRowResponse(diffType = "REMOVED", values = row.values)
                    is DiffRow.Changed -> DiffRowResponse(
                        diffType = "CHANGED",
                        left = row.left,
                        right = row.right,
                        changedColumns = row.changedColumns,
                    )
                }
            },
            totalRows = result.totalRows,
            summary = DiffSummaryResponse(result.summary.added, result.summary.removed, result.summary.changed),
        )
    }
}

data class ColumnResponse(val name: String, val type: String)
