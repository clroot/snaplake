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
        val pkJoin = primaryKeys.joinToString(" AND ") { pk ->
            "l.\"${escapeIdentifier(pk)}\" = r.\"${escapeIdentifier(pk)}\""
        }
        val firstPk = escapeIdentifier(primaryKeys.first())
        val nonPkCols = columns.map { it.name }.filter { it !in primaryKeys }

        val changeCondition = if (nonPkCols.isNotEmpty()) {
            nonPkCols.joinToString(" OR ") { col ->
                "l.\"${escapeIdentifier(col)}\" IS DISTINCT FROM r.\"${escapeIdentifier(col)}\""
            }
        } else {
            "FALSE"
        }

        val leftCols = columns.joinToString(", ") {
            "l.\"${escapeIdentifier(it.name)}\" as \"l_${escapeIdentifier(it.name)}\""
        }
        val rightCols = columns.joinToString(", ") {
            "r.\"${escapeIdentifier(it.name)}\" as \"r_${escapeIdentifier(it.name)}\""
        }
        val orderBy = primaryKeys.joinToString(", ") {
            "COALESCE(\"l_${escapeIdentifier(it)}\", \"r_${escapeIdentifier(it)}\")"
        }

        val whereClause = """
            l."$firstPk" IS NULL
            OR r."$firstPk" IS NULL
            OR ($changeCondition)
        """.trimIndent()

        // Single CTE for both summary and paginated data
        val sql = """
            WITH diff AS (
                SELECT
                    CASE
                        WHEN l."$firstPk" IS NULL THEN 'ADDED'
                        WHEN r."$firstPk" IS NULL THEN 'REMOVED'
                        ELSE 'CHANGED'
                    END as _diff_type,
                    $leftCols, $rightCols
                FROM '$leftUri' l
                FULL OUTER JOIN '$rightUri' r ON $pkJoin
                WHERE $whereClause
            )
            SELECT
                (SELECT COUNT(*) FROM diff) as _total,
                (SELECT COUNT(*) FILTER (WHERE _diff_type = 'ADDED') FROM diff) as _added,
                (SELECT COUNT(*) FILTER (WHERE _diff_type = 'REMOVED') FROM diff) as _removed,
                (SELECT COUNT(*) FILTER (WHERE _diff_type = 'CHANGED') FROM diff) as _changed,
                d.*
            FROM diff d
            ORDER BY $orderBy
        """.trimIndent()

        val result = queryEngine.executeQuery(sql, storageConfig, limit, offset)
        val colCount = columns.size

        val firstRow = result.rows.firstOrNull()
        val totalRows = (firstRow?.get(0) as? Number)?.toLong() ?: 0
        val summary = DiffSummary(
            added = (firstRow?.get(1) as? Number)?.toLong() ?: 0,
            removed = (firstRow?.get(2) as? Number)?.toLong() ?: 0,
            changed = (firstRow?.get(3) as? Number)?.toLong() ?: 0,
        )

        // Skip first 4 columns (total, added, removed, changed) + _diff_type
        val metaColCount = 4
        val diffRows = result.rows.map { row ->
            val diffType = row[metaColCount] as String
            val leftValues = row.subList(metaColCount + 1, metaColCount + 1 + colCount)
            val rightValues = row.subList(metaColCount + 1 + colCount, metaColCount + 1 + 2 * colCount)

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

        return UnifiedDiffResult(columns, primaryKeys, diffRows, totalRows, summary)
    }

    private fun compareWithExcept(
        leftUri: String,
        rightUri: String,
        storageConfig: StorageConfig?,
        columns: List<ColumnSchema>,
        limit: Int,
        offset: Int,
    ): UnifiedDiffResult {
        val allCols = columns.joinToString(", ") { "\"${escapeIdentifier(it.name)}\"" }

        // Single CTE: compute diff + summary in one scan
        val sql = """
            WITH diff AS (
                SELECT 'REMOVED' as _diff_type, $allCols FROM (SELECT * FROM '$leftUri' EXCEPT SELECT * FROM '$rightUri')
                UNION ALL
                SELECT 'ADDED' as _diff_type, $allCols FROM (SELECT * FROM '$rightUri' EXCEPT SELECT * FROM '$leftUri')
            )
            SELECT
                (SELECT COUNT(*) FROM diff) as _total,
                (SELECT COUNT(*) FILTER (WHERE _diff_type = 'ADDED') FROM diff) as _added,
                (SELECT COUNT(*) FILTER (WHERE _diff_type = 'REMOVED') FROM diff) as _removed,
                d.*
            FROM diff d
            ORDER BY _diff_type, $allCols
        """.trimIndent()

        val result = queryEngine.executeQuery(sql, storageConfig, limit, offset)

        val firstRow = result.rows.firstOrNull()
        val totalRows = (firstRow?.get(0) as? Number)?.toLong() ?: 0
        val addedCount = (firstRow?.get(1) as? Number)?.toLong() ?: 0
        val removedCount = (firstRow?.get(2) as? Number)?.toLong() ?: 0

        // Skip first 3 meta columns (total, added, removed) + _diff_type
        val metaColCount = 3
        val diffRows = result.rows.map { row ->
            val diffType = row[metaColCount] as String
            val values = row.subList(metaColCount + 1, row.size)
            if (diffType == "ADDED") DiffRow.Added(values) else DiffRow.Removed(values)
        }

        return UnifiedDiffResult(
            columns, emptyList(), diffRows, totalRows,
            DiffSummary(added = addedCount, removed = removedCount, changed = 0),
        )
    }

    private fun escapeIdentifier(name: String): String = name.replace("\"", "\"\"")

    private fun resolveTableUri(snapshotId: SnapshotId, tableName: String): String {
        val snapshot = loadSnapshotPort.findById(snapshotId)
            ?: throw SnapshotNotFoundException(snapshotId)

        val table = snapshot.tables.find {
            "${it.schema}.${it.table}" == tableName || it.table == tableName
        } ?: throw IllegalArgumentException("Table '$tableName' not found in snapshot ${snapshotId.value}")

        return storageProvider.getUri(table.storagePath)
    }
}
