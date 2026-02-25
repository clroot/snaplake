package com.snaplake.adapter.inbound.web.dto

import com.snaplake.application.port.inbound.ColumnStat
import com.snaplake.application.port.inbound.RowsCompareResult
import com.snaplake.application.port.inbound.StatsResult

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
