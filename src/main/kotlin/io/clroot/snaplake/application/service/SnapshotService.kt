package io.clroot.snaplake.application.service

import io.clroot.snaplake.application.port.inbound.DeleteSnapshotUseCase
import io.clroot.snaplake.application.port.inbound.GetSnapshotUseCase
import io.clroot.snaplake.application.port.inbound.RecoverOrphanedSnapshotsUseCase
import io.clroot.snaplake.application.port.inbound.TakeSnapshotUseCase
import io.clroot.snaplake.application.port.inbound.UpdateSnapshotMetadataUseCase
import io.clroot.snaplake.application.port.outbound.*
import io.clroot.snaplake.domain.exception.DatasourceNotFoundException
import io.clroot.snaplake.domain.exception.SnapshotAlreadyRunningException
import io.clroot.snaplake.domain.exception.SnapshotNotFoundException
import io.clroot.snaplake.domain.model.*
import io.clroot.snaplake.domain.vo.DatasourceId
import io.clroot.snaplake.domain.vo.SnapshotId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

@Service
class SnapshotService(
    private val loadDatasourcePort: LoadDatasourcePort,
    private val saveSnapshotPort: SaveSnapshotPort,
    private val loadSnapshotPort: LoadSnapshotPort,
    private val storageProvider: StorageProvider,
    private val dialectRegistry: DatabaseDialectRegistry,
    private val encryptionPort: EncryptionPort,
    private val parquetWritePort: ParquetWritePort,
) : TakeSnapshotUseCase,
    GetSnapshotUseCase,
    DeleteSnapshotUseCase,
    RecoverOrphanedSnapshotsUseCase,
    UpdateSnapshotMetadataUseCase {
    private val log = LoggerFactory.getLogger(javaClass)
    private val activeDatasources = ConcurrentHashMap.newKeySet<DatasourceId>()

    override fun takeSnapshot(datasourceId: DatasourceId): SnapshotMeta {
        if (!activeDatasources.add(datasourceId)) throw SnapshotAlreadyRunningException(datasourceId)
        try {
            return createSnapshot(datasourceId)
        } finally {
            activeDatasources.remove(datasourceId)
        }
    }

    private fun createSnapshot(datasourceId: DatasourceId): SnapshotMeta {
        val datasource =
            loadDatasourcePort.findById(datasourceId)
                ?: throw DatasourceNotFoundException(datasourceId)

        val runningSnapshot = loadSnapshotPort.findByDatasourceIdAndStatus(datasourceId, SnapshotStatus.RUNNING)
        if (runningSnapshot != null) {
            val staleThreshold = Instant.now().minus(STALE_SNAPSHOT_TIMEOUT)
            if (runningSnapshot.startedAt.isBefore(staleThreshold)) {
                runningSnapshot.fail("Snapshot timed out (stale RUNNING state detected)")
                saveSnapshotPort.save(runningSnapshot)
                log.warn("Marked stale RUNNING snapshot {} as FAILED", runningSnapshot.id.value)
            } else {
                throw SnapshotAlreadyRunningException(datasourceId)
            }
        }

        val snapshotDate = LocalDate.now()
        val snapshotType = SnapshotType.DAILY

        val snapshot =
            SnapshotMeta.start(
                datasourceId = datasource.id,
                datasourceName = datasource.name,
                snapshotType = snapshotType,
                snapshotDate = snapshotDate,
            )
        val errors = mutableListOf<String>()
        var failureMessage = "Snapshot terminated unexpectedly"
        var finalStateSaved = false

        try {
            saveSnapshotPort.save(snapshot)
            val dialect = dialectRegistry.getDialect(datasource.type)
            val decryptedPassword = encryptionPort.decrypt(datasource.encryptedPassword)

            try {
                dialect.createConnection(datasource, decryptedPassword).use { conn ->
                    for (schema in datasource.schemas) {
                        val allTables =
                            try {
                                dialect.listTables(conn, schema)
                            } catch (e: Exception) {
                                log.error("Failed to list tables for schema '{}': {}", schema, e.message)
                                errors.add("Failed to list tables for schema '$schema': ${e.message}")
                                continue
                            }

                        val selected = datasource.includedTables[schema]
                        val tables =
                            if (selected.isNullOrEmpty()) {
                                allTables
                            } else {
                                allTables.filter { it.name in selected }
                            }

                        for (table in tables) {
                            try {
                                val rs =
                                    conn.createStatement().executeQuery(
                                        "SELECT * FROM \"${table.schema}\".\"${table.name}\"",
                                    )
                                val result = parquetWritePort.writeResultSetToParquet(rs)
                                rs.close()

                                val storagePath =
                                    buildStoragePath(
                                        datasource.name,
                                        snapshotType,
                                        snapshotDate,
                                        snapshot.id,
                                        table.schema,
                                        table.name,
                                    )
                                storageProvider.write(storagePath, result.data)

                                val primaryKeys =
                                    try {
                                        dialect.listPrimaryKeys(conn, table.schema, table.name)
                                    } catch (e: Exception) {
                                        log.warn("Failed to get PKs for {}.{}: {}", table.schema, table.name, e.message)
                                        emptyList()
                                    }

                                snapshot.addTable(
                                    TableMeta(
                                        schema = table.schema,
                                        table = table.name,
                                        rowCount = result.rowCount,
                                        sizeBytes = result.data.size.toLong(),
                                        storagePath = storagePath,
                                        primaryKeys = primaryKeys,
                                    ),
                                )

                                log.info(
                                    "Snapshot table {}.{}: {} rows, {} bytes",
                                    table.schema,
                                    table.name,
                                    result.rowCount,
                                    result.data.size,
                                )
                            } catch (e: Exception) {
                                log.error(
                                    "Failed to snapshot table {}.{}: {}",
                                    table.schema,
                                    table.name,
                                    e.message,
                                )
                                errors.add("Failed to snapshot ${table.schema}.${table.name}: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to connect to datasource '{}': {}", datasource.name, e.message)
                snapshot.fail("Connection failed: ${e.message}")
                saveSnapshotPort.save(snapshot)
                finalStateSaved = true
                return snapshot
            }

            if (errors.isNotEmpty()) {
                snapshot.fail(errors.joinToString("; "))
            } else {
                snapshot.complete()
            }
            saveSnapshotPort.save(snapshot)
            finalStateSaved = true

            // Handle monthly snapshots (1st of month)
            if (snapshotDate.dayOfMonth == 1) {
                copyToMonthly(datasource, snapshot, snapshotDate)
            }

            // Apply retention policy
            applyRetention(datasource)

            return snapshot
        } catch (e: Exception) {
            failureMessage = "Snapshot failed: ${e.message}"
            throw e
        } finally {
            // The in-memory state may already be terminal while the database still says RUNNING.
            if (!finalStateSaved) {
                try {
                    if (snapshot.status == SnapshotStatus.RUNNING) snapshot.fail(failureMessage)
                    saveSnapshotPort.save(snapshot)
                    log.error("Snapshot {} did not finish normally, saved as {}", snapshot.id.value, snapshot.status)
                } catch (ex: Exception) {
                    log.error(
                        "event=snapshot_failure_persistence_failed snapshotId={} datasourceId={}",
                        snapshot.id.value,
                        datasourceId.value,
                        ex,
                    )
                }
            }
        }
    }

    @Transactional(readOnly = true)
    override fun getById(id: SnapshotId): SnapshotMeta =
        loadSnapshotPort.findById(id)
            ?: throw SnapshotNotFoundException(id)

    @Transactional(readOnly = true)
    override fun getAll(): List<SnapshotMeta> = loadSnapshotPort.findAll()

    @Transactional(readOnly = true)
    override fun getByDatasourceId(datasourceId: DatasourceId): List<SnapshotMeta> = loadSnapshotPort.findByDatasourceId(datasourceId)

    override fun delete(id: SnapshotId) {
        val snapshot =
            loadSnapshotPort.findById(id)
                ?: throw SnapshotNotFoundException(id)

        // Delete storage files
        for (table in snapshot.tables) {
            try {
                storageProvider.delete(table.storagePath)
            } catch (e: Exception) {
                log.warn("Failed to delete storage file {}: {}", table.storagePath, e.message)
            }
        }

        loadSnapshotPort.deleteById(id)
    }

    override fun updateMetadata(command: UpdateSnapshotMetadataUseCase.Command): SnapshotMeta {
        val snapshot =
            loadSnapshotPort.findById(command.snapshotId)
                ?: throw SnapshotNotFoundException(command.snapshotId)

        if (command.tags != null) {
            snapshot.updateTags(command.tags)
        }
        if (command.memo != null) {
            snapshot.updateMemo(command.memo)
        }

        saveSnapshotPort.save(snapshot)
        return snapshot
    }

    override fun recoverAll(): Int {
        val runningSnapshots = loadSnapshotPort.findAllByStatus(SnapshotStatus.RUNNING)
        runningSnapshots.forEach { snapshot ->
            snapshot.fail("Recovered on server startup (orphaned RUNNING state)")
            saveSnapshotPort.save(snapshot)
            log.warn("Recovered orphaned RUNNING snapshot {} on startup", snapshot.id.value)
        }
        return runningSnapshots.size
    }

    override fun recoverStale(): Int {
        val staleThreshold = Instant.now().minus(STALE_SNAPSHOT_TIMEOUT)
        val runningSnapshots = loadSnapshotPort.findAllByStatus(SnapshotStatus.RUNNING)
        val staleSnapshots = runningSnapshots.filter { it.startedAt.isBefore(staleThreshold) }
        staleSnapshots.forEach { snapshot ->
            snapshot.fail("Snapshot timed out (stale RUNNING state detected by background scan)")
            saveSnapshotPort.save(snapshot)
            log.warn("Recovered stale RUNNING snapshot {} (started at {})", snapshot.id.value, snapshot.startedAt)
        }
        return staleSnapshots.size
    }

    private fun buildStoragePath(
        datasourceName: String,
        snapshotType: SnapshotType,
        snapshotDate: LocalDate,
        snapshotId: SnapshotId,
        schema: String,
        table: String,
    ): String = "$datasourceName/${snapshotType.name.lowercase()}/$snapshotDate/${snapshotId.value}/$schema.$table.parquet"

    private fun copyToMonthly(
        datasource: Datasource,
        dailySnapshot: SnapshotMeta,
        date: LocalDate,
    ) {
        try {
            val monthlySnapshot =
                SnapshotMeta.start(
                    datasourceId = datasource.id,
                    datasourceName = datasource.name,
                    snapshotType = SnapshotType.MONTHLY,
                    snapshotDate = date,
                )

            for (table in dailySnapshot.tables) {
                val data = storageProvider.read(table.storagePath)
                val monthlyPath =
                    buildStoragePath(
                        datasource.name,
                        SnapshotType.MONTHLY,
                        date,
                        monthlySnapshot.id,
                        table.schema,
                        table.table,
                    )
                storageProvider.write(monthlyPath, data)
                monthlySnapshot.addTable(
                    TableMeta(
                        schema = table.schema,
                        table = table.table,
                        rowCount = table.rowCount,
                        sizeBytes = table.sizeBytes,
                        storagePath = monthlyPath,
                        primaryKeys = table.primaryKeys,
                    ),
                )
            }

            monthlySnapshot.complete()
            saveSnapshotPort.save(monthlySnapshot)
            log.info("Monthly snapshot created for datasource '{}'", datasource.name)
        } catch (e: Exception) {
            log.error("Failed to create monthly snapshot: {}", e.message)
        }
    }

    private fun applyRetention(datasource: Datasource) {
        val policy = datasource.retentionPolicy
        if (policy.dailyMaxCount <= 0 && policy.monthlyMaxCount <= 0) return

        val snapshots =
            loadSnapshotPort
                .findByDatasourceId(datasource.id)
                .filter { it.status == SnapshotStatus.COMPLETED }
                .sortedByDescending { it.snapshotDate }

        if (policy.dailyMaxCount > 0) {
            val dailySnapshots = snapshots.filter { it.snapshotType == SnapshotType.DAILY }
            if (dailySnapshots.size > policy.dailyMaxCount) {
                dailySnapshots.drop(policy.dailyMaxCount).forEach { old ->
                    try {
                        delete(old.id)
                        log.info("Deleted old daily snapshot: {}", old.id.value)
                    } catch (e: Exception) {
                        log.warn("Failed to delete old snapshot {}: {}", old.id.value, e.message)
                    }
                }
            }
        }

        if (policy.monthlyMaxCount > 0) {
            val monthlySnapshots = snapshots.filter { it.snapshotType == SnapshotType.MONTHLY }
            if (monthlySnapshots.size > policy.monthlyMaxCount) {
                monthlySnapshots.drop(policy.monthlyMaxCount).forEach { old ->
                    try {
                        delete(old.id)
                        log.info("Deleted old monthly snapshot: {}", old.id.value)
                    } catch (e: Exception) {
                        log.warn("Failed to delete old snapshot {}: {}", old.id.value, e.message)
                    }
                }
            }
        }
    }

    companion object {
        private val STALE_SNAPSHOT_TIMEOUT = Duration.ofMinutes(30)
    }
}
