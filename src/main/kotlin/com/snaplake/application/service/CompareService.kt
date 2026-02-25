package com.snaplake.application.service

import com.snaplake.application.port.inbound.*
import com.snaplake.application.port.outbound.LoadSnapshotPort
import com.snaplake.application.port.outbound.LoadStorageConfigPort
import com.snaplake.application.port.outbound.QueryEngine
import com.snaplake.application.port.outbound.QueryResult
import com.snaplake.application.port.outbound.StorageProvider
import com.snaplake.domain.exception.SnapshotNotFoundException
import com.snaplake.domain.vo.SnapshotId
import org.springframework.stereotype.Service

@Service
class CompareService(
    private val queryEngine: QueryEngine,
    private val loadSnapshotPort: LoadSnapshotPort,
    private val loadStorageConfigPort: LoadStorageConfigPort,
    private val storageProvider: StorageProvider,
) : CompareStatsUseCase, CompareRowsUseCase, CompareDiffUseCase {

    override fun compareStats(command: CompareStatsUseCase.Command): StatsResult {
        val leftUri = resolveTableUri(command.leftSnapshotId, command.tableName)
        val rightUri = resolveTableUri(command.rightSnapshotId, command.tableName)
        val storageConfig = loadStorageConfigPort.find()

        val leftCount = queryEngine.countRows(leftUri, storageConfig)
        val rightCount = queryEngine.countRows(rightUri, storageConfig)

        val columns = queryEngine.describeTable(leftUri, storageConfig)

        val columnStats = columns.map { col ->
            val statsSql = """
                SELECT 
                    (SELECT COUNT(DISTINCT "${col.name}") FROM '$leftUri') as left_distinct,
                    (SELECT COUNT(DISTINCT "${col.name}") FROM '$rightUri') as right_distinct,
                    (SELECT SUM(CASE WHEN "${col.name}" IS NULL THEN 1 ELSE 0 END) FROM '$leftUri') as left_null,
                    (SELECT SUM(CASE WHEN "${col.name}" IS NULL THEN 1 ELSE 0 END) FROM '$rightUri') as right_null
            """.trimIndent()

            try {
                val result = queryEngine.executeQuery(statsSql, storageConfig, 1, 0)
                val row = result.rows.firstOrNull()
                ColumnStat(
                    column = col.name,
                    leftDistinctCount = (row?.get(0) as? Number)?.toLong() ?: 0,
                    rightDistinctCount = (row?.get(1) as? Number)?.toLong() ?: 0,
                    leftNullCount = (row?.get(2) as? Number)?.toLong() ?: 0,
                    rightNullCount = (row?.get(3) as? Number)?.toLong() ?: 0,
                )
            } catch (e: Exception) {
                ColumnStat(col.name, 0, 0, 0, 0)
            }
        }

        return StatsResult(
            leftRowCount = leftCount,
            rightRowCount = rightCount,
            columnStats = columnStats,
        )
    }

    override fun compareRows(command: CompareRowsUseCase.Command): RowsCompareResult {
        val leftUri = resolveTableUri(command.leftSnapshotId, command.tableName)
        val rightUri = resolveTableUri(command.rightSnapshotId, command.tableName)
        val storageConfig = loadStorageConfigPort.find()

        val addedSql = "SELECT * FROM '$rightUri' EXCEPT SELECT * FROM '$leftUri'"
        val removedSql = "SELECT * FROM '$leftUri' EXCEPT SELECT * FROM '$rightUri'"

        val added = queryEngine.executeQuery(addedSql, storageConfig, command.limit, command.offset)
        val removed = queryEngine.executeQuery(removedSql, storageConfig, command.limit, command.offset)

        return RowsCompareResult(added = added, removed = removed)
    }

    override fun compareDiff(command: CompareDiffUseCase.Command): QueryResult {
        val leftUri = resolveTableUri(command.leftSnapshotId, command.tableName)
        val rightUri = resolveTableUri(command.rightSnapshotId, command.tableName)
        val storageConfig = loadStorageConfigPort.find()

        val pkJoinCondition = command.primaryKeys.joinToString(" AND ") { pk ->
            "l.\"$pk\" = r.\"$pk\""
        }

        val columns = queryEngine.describeTable(leftUri, storageConfig)
        val nonPkColumns = columns.map { it.name }.filter { it !in command.primaryKeys }

        val changeConditions = if (nonPkColumns.isNotEmpty()) {
            nonPkColumns.joinToString(" OR ") { col ->
                "l.\"$col\" IS DISTINCT FROM r.\"$col\""
            }
        } else {
            "FALSE"
        }

        val firstPk = command.primaryKeys.first()

        val diffSql = """
            SELECT 
                CASE 
                    WHEN l."$firstPk" IS NULL THEN 'ADDED'
                    WHEN r."$firstPk" IS NULL THEN 'REMOVED'
                    ELSE 'CHANGED'
                END as _diff_type,
                ${columns.joinToString(", ") { "COALESCE(r.\"${it.name}\", l.\"${it.name}\") as \"${it.name}\"" }}
            FROM '$leftUri' l
            FULL OUTER JOIN '$rightUri' r ON $pkJoinCondition
            WHERE l."$firstPk" IS NULL 
               OR r."$firstPk" IS NULL
               OR ($changeConditions)
        """.trimIndent()

        return queryEngine.executeQuery(diffSql, storageConfig, command.limit, command.offset)
    }

    private fun resolveTableUri(snapshotId: SnapshotId, tableName: String): String {
        val snapshot = loadSnapshotPort.findById(snapshotId)
            ?: throw SnapshotNotFoundException(snapshotId)

        val table = snapshot.tables.find {
            "${it.schema}.${it.table}" == tableName || it.table == tableName
        } ?: throw IllegalArgumentException("Table '$tableName' not found in snapshot ${snapshotId.value}")

        return storageProvider.getUri(table.storagePath)
    }
}
