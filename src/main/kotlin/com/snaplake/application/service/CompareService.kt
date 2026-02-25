package com.snaplake.application.service

import com.snaplake.application.port.inbound.*
import com.snaplake.application.port.outbound.*
import com.snaplake.domain.exception.SnapshotNotFoundException
import com.snaplake.domain.model.StorageConfig
import com.snaplake.domain.vo.SnapshotId
import org.springframework.stereotype.Service

@Service
class CompareService(
    private val queryEngine: QueryEngine,
    private val loadSnapshotPort: LoadSnapshotPort,
    private val loadStorageConfigPort: LoadStorageConfigPort,
    private val storageProvider: StorageProvider,
) : CompareStatsUseCase, CompareRowsUseCase, CompareDiffUseCase, CompareUnifiedDiffUseCase {

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

    override fun compareUnifiedDiff(command: CompareUnifiedDiffUseCase.Command): UnifiedDiffResult {
        val leftUri = resolveTableUri(command.leftSnapshotId, command.tableName)
        val rightUri = resolveTableUri(command.rightSnapshotId, command.tableName)
        val storageConfig = loadStorageConfigPort.find()

        val leftSnapshot = loadSnapshotPort.findById(command.leftSnapshotId)
            ?: throw SnapshotNotFoundException(command.leftSnapshotId)

        val leftTable = leftSnapshot.tables.find {
            "${it.schema}.${it.table}" == command.tableName || it.table == command.tableName
        } ?: throw IllegalArgumentException("Table '${command.tableName}' not found in left snapshot")

        val primaryKeys = leftTable.primaryKeys
        val columns = queryEngine.describeTable(leftUri, storageConfig)

        return if (primaryKeys.isNotEmpty()) {
            compareWithPK(leftUri, rightUri, storageConfig, columns, primaryKeys, command.limit, command.offset)
        } else {
            compareWithExcept(leftUri, rightUri, storageConfig, columns, command.limit, command.offset)
        }
    }

    private fun compareWithPK(
        leftUri: String,
        rightUri: String,
        storageConfig: StorageConfig?,
        columns: List<ColumnSchema>,
        primaryKeys: List<String>,
        limit: Int,
        offset: Int,
    ): UnifiedDiffResult {
        val pkJoin = primaryKeys.joinToString(" AND ") { "l.\"$it\" = r.\"$it\"" }
        val firstPk = primaryKeys.first()
        val nonPkCols = columns.map { it.name }.filter { it !in primaryKeys }

        val changeCondition = if (nonPkCols.isNotEmpty()) {
            nonPkCols.joinToString(" OR ") { "l.\"$it\" IS DISTINCT FROM r.\"$it\"" }
        } else {
            "FALSE"
        }

        val leftCols = columns.joinToString(", ") { "l.\"${it.name}\" as \"l_${it.name}\"" }
        val rightCols = columns.joinToString(", ") { "r.\"${it.name}\" as \"r_${it.name}\"" }
        val orderBy = primaryKeys.joinToString(", ") { "COALESCE(l.\"$it\", r.\"$it\")" }

        val sql = """
            SELECT
                CASE
                    WHEN l."$firstPk" IS NULL THEN 'ADDED'
                    WHEN r."$firstPk" IS NULL THEN 'REMOVED'
                    ELSE 'CHANGED'
                END as _diff_type,
                $leftCols, $rightCols
            FROM '$leftUri' l
            FULL OUTER JOIN '$rightUri' r ON $pkJoin
            WHERE l."$firstPk" IS NULL
               OR r."$firstPk" IS NULL
               OR ($changeCondition)
            ORDER BY $orderBy
        """.trimIndent()

        val result = queryEngine.executeQuery(sql, storageConfig, limit, offset)
        val colCount = columns.size

        val summarySql = """
            SELECT
                SUM(CASE WHEN l."$firstPk" IS NULL THEN 1 ELSE 0 END),
                SUM(CASE WHEN r."$firstPk" IS NULL THEN 1 ELSE 0 END),
                SUM(CASE WHEN l."$firstPk" IS NOT NULL AND r."$firstPk" IS NOT NULL THEN 1 ELSE 0 END)
            FROM '$leftUri' l
            FULL OUTER JOIN '$rightUri' r ON $pkJoin
            WHERE l."$firstPk" IS NULL
               OR r."$firstPk" IS NULL
               OR ($changeCondition)
        """.trimIndent()

        val summaryRow = queryEngine.executeQuery(summarySql, storageConfig, 1, 0).rows.firstOrNull()
        val summary = DiffSummary(
            added = (summaryRow?.get(0) as? Number)?.toLong() ?: 0,
            removed = (summaryRow?.get(1) as? Number)?.toLong() ?: 0,
            changed = (summaryRow?.get(2) as? Number)?.toLong() ?: 0,
        )

        val diffRows = result.rows.map { row ->
            val diffType = row[0] as String
            val leftValues = row.subList(1, 1 + colCount)
            val rightValues = row.subList(1 + colCount, 1 + 2 * colCount)

            when (diffType) {
                "ADDED" -> DiffRow.Added(rightValues)
                "REMOVED" -> DiffRow.Removed(leftValues)
                else -> {
                    val changedCols = columns.indices.filter { i ->
                        leftValues[i]?.toString() != rightValues[i]?.toString()
                    }
                    DiffRow.Changed(leftValues, rightValues, changedCols)
                }
            }
        }

        return UnifiedDiffResult(columns, primaryKeys, diffRows, result.totalRows, summary)
    }

    private fun compareWithExcept(
        leftUri: String,
        rightUri: String,
        storageConfig: StorageConfig?,
        columns: List<ColumnSchema>,
        limit: Int,
        offset: Int,
    ): UnifiedDiffResult {
        val unionSql = """
            SELECT 'REMOVED' as _diff_type, * FROM (SELECT * FROM '$leftUri' EXCEPT SELECT * FROM '$rightUri')
            UNION ALL
            SELECT 'ADDED' as _diff_type, * FROM (SELECT * FROM '$rightUri' EXCEPT SELECT * FROM '$leftUri')
        """.trimIndent()

        val result = queryEngine.executeQuery(unionSql, storageConfig, limit, offset)

        val addedCount = queryEngine.executeQuery(
            "SELECT COUNT(*) FROM (SELECT * FROM '$rightUri' EXCEPT SELECT * FROM '$leftUri')",
            storageConfig, 1, 0,
        ).rows.firstOrNull()?.get(0) as? Number ?: 0
        val removedCount = queryEngine.executeQuery(
            "SELECT COUNT(*) FROM (SELECT * FROM '$leftUri' EXCEPT SELECT * FROM '$rightUri')",
            storageConfig, 1, 0,
        ).rows.firstOrNull()?.get(0) as? Number ?: 0

        val diffRows = result.rows.map { row ->
            val diffType = row[0] as String
            val values = row.subList(1, row.size)
            if (diffType == "ADDED") DiffRow.Added(values) else DiffRow.Removed(values)
        }

        return UnifiedDiffResult(
            columns, emptyList(), diffRows, result.totalRows,
            DiffSummary(added = addedCount.toLong(), removed = removedCount.toLong(), changed = 0),
        )
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
