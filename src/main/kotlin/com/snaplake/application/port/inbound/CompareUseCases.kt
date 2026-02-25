package com.snaplake.application.port.inbound

import com.snaplake.application.port.outbound.QueryResult
import com.snaplake.domain.vo.SnapshotId

data class StatsResult(
    val leftRowCount: Long,
    val rightRowCount: Long,
    val columnStats: List<ColumnStat>,
)

data class ColumnStat(
    val column: String,
    val leftDistinctCount: Long,
    val rightDistinctCount: Long,
    val leftNullCount: Long,
    val rightNullCount: Long,
)

interface CompareStatsUseCase {
    fun compareStats(command: Command): StatsResult

    data class Command(
        val leftSnapshotId: SnapshotId,
        val rightSnapshotId: SnapshotId,
        val tableName: String,
    )
}

interface CompareRowsUseCase {
    fun compareRows(command: Command): RowsCompareResult

    data class Command(
        val leftSnapshotId: SnapshotId,
        val rightSnapshotId: SnapshotId,
        val tableName: String,
        val limit: Int = 100,
        val offset: Int = 0,
    )
}

data class RowsCompareResult(
    val added: QueryResult,
    val removed: QueryResult,
)

interface CompareDiffUseCase {
    fun compareDiff(command: Command): QueryResult

    data class Command(
        val leftSnapshotId: SnapshotId,
        val rightSnapshotId: SnapshotId,
        val tableName: String,
        val primaryKeys: List<String>,
        val limit: Int = 100,
        val offset: Int = 0,
    )
}
