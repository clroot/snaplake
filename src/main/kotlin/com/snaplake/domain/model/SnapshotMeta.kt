package com.snaplake.domain.model

import com.snaplake.domain.vo.DatasourceId
import com.snaplake.domain.vo.SnapshotId
import java.time.Instant
import java.time.LocalDate

enum class SnapshotType { DAILY, MONTHLY }
enum class SnapshotStatus { RUNNING, COMPLETED, FAILED }

data class TableMeta(
    val schema: String,
    val table: String,
    val rowCount: Long,
    val sizeBytes: Long,
    val storagePath: String,
    val primaryKeys: List<String> = emptyList(),
)

class SnapshotMeta private constructor(
    val id: SnapshotId,
    val datasourceId: DatasourceId,
    val datasourceName: String,
    val snapshotType: SnapshotType,
    val snapshotDate: LocalDate,
    val startedAt: Instant,
    status: SnapshotStatus,
    completedAt: Instant?,
    errorMessage: String?,
) {
    var status: SnapshotStatus = status
        private set

    var completedAt: Instant? = completedAt
        private set

    var errorMessage: String? = errorMessage
        private set

    private val _tables: MutableList<TableMeta> = mutableListOf()
    val tables: List<TableMeta> get() = _tables.toList()

    fun addTable(table: TableMeta) {
        require(status == SnapshotStatus.RUNNING) { "Cannot add tables to a non-running snapshot" }
        _tables.add(table)
    }

    fun complete() {
        require(status == SnapshotStatus.RUNNING) { "Only RUNNING snapshots can be completed" }
        status = SnapshotStatus.COMPLETED
        completedAt = Instant.now()
    }

    fun fail(message: String) {
        require(status == SnapshotStatus.RUNNING) { "Only RUNNING snapshots can be marked as failed" }
        status = SnapshotStatus.FAILED
        completedAt = Instant.now()
        errorMessage = message
    }

    companion object {
        fun start(
            datasourceId: DatasourceId,
            datasourceName: String,
            snapshotType: SnapshotType,
            snapshotDate: LocalDate,
        ): SnapshotMeta = SnapshotMeta(
            id = SnapshotId.generate(),
            datasourceId = datasourceId,
            datasourceName = datasourceName,
            snapshotType = snapshotType,
            snapshotDate = snapshotDate,
            startedAt = Instant.now(),
            status = SnapshotStatus.RUNNING,
            completedAt = null,
            errorMessage = null,
        )

        fun reconstitute(
            id: SnapshotId,
            datasourceId: DatasourceId,
            datasourceName: String,
            snapshotType: SnapshotType,
            snapshotDate: LocalDate,
            startedAt: Instant,
            status: SnapshotStatus,
            completedAt: Instant?,
            errorMessage: String?,
            tables: List<TableMeta>,
        ): SnapshotMeta = SnapshotMeta(
            id, datasourceId, datasourceName, snapshotType,
            snapshotDate, startedAt, status, completedAt, errorMessage,
        ).also { it._tables.addAll(tables) }
    }
}
