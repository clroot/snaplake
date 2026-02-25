package com.snaplake.application.service

import com.snaplake.application.port.inbound.DescribeTableUseCase
import com.snaplake.application.port.inbound.ExecuteQueryUseCase
import com.snaplake.application.port.inbound.PreviewTableUseCase
import com.snaplake.application.port.outbound.ColumnSchema
import com.snaplake.application.port.outbound.LoadSnapshotPort
import com.snaplake.application.port.outbound.LoadStorageConfigPort
import com.snaplake.application.port.outbound.QueryEngine
import com.snaplake.application.port.outbound.QueryResult
import com.snaplake.application.port.outbound.StorageProvider
import com.snaplake.domain.exception.SnapshotNotFoundException
import com.snaplake.domain.vo.SnapshotId
import org.springframework.stereotype.Service

@Service
class QueryService(
    private val queryEngine: QueryEngine,
    private val loadSnapshotPort: LoadSnapshotPort,
    private val loadStorageConfigPort: LoadStorageConfigPort,
    private val storageProvider: StorageProvider,
) : ExecuteQueryUseCase, DescribeTableUseCase, PreviewTableUseCase {

    override fun executeQuery(command: ExecuteQueryUseCase.Command): QueryResult {
        val storageConfig = loadStorageConfigPort.find()
        val viewSetupSql = command.context?.let { buildViewSetupSql(it) } ?: emptyList()

        return queryEngine.executeQuery(
            sql = command.sql,
            storageConfig = storageConfig,
            limit = command.limit,
            offset = command.offset,
            viewSetupSql = viewSetupSql,
        )
    }

    override fun describe(snapshotId: SnapshotId, tableName: String): List<ColumnSchema> {
        val snapshot = loadSnapshotPort.findById(snapshotId)
            ?: throw SnapshotNotFoundException(snapshotId)

        val table = snapshot.tables.find { "${it.schema}.${it.table}" == tableName || it.table == tableName }
            ?: throw IllegalArgumentException("Table '$tableName' not found in snapshot")

        val uri = storageProvider.getUri(table.storagePath)
        val storageConfig = loadStorageConfigPort.find()
        return queryEngine.describeTable(uri, storageConfig)
    }

    override fun describeAll(snapshotId: SnapshotId): Map<String, List<ColumnSchema>> {
        val snapshot = loadSnapshotPort.findById(snapshotId)
            ?: throw SnapshotNotFoundException(snapshotId)

        val storageConfig = loadStorageConfigPort.find()
        return snapshot.tables.associate { table ->
            val uri = storageProvider.getUri(table.storagePath)
            table.table to queryEngine.describeTable(uri, storageConfig)
        }
    }

    override fun preview(command: PreviewTableUseCase.Command): QueryResult {
        val snapshot = loadSnapshotPort.findById(command.snapshotId)
            ?: throw SnapshotNotFoundException(command.snapshotId)

        val table = snapshot.tables.find {
            "${it.schema}.${it.table}" == command.tableName || it.table == command.tableName
        } ?: throw IllegalArgumentException("Table '${command.tableName}' not found in snapshot")

        val uri = storageProvider.getUri(table.storagePath)
        val storageConfig = loadStorageConfigPort.find()
        return queryEngine.previewTable(
            uri = uri,
            storageConfig = storageConfig,
            where = command.where,
            orderBy = command.orderBy,
            limit = command.limit,
            offset = command.offset,
        )
    }

    private fun buildViewSetupSql(context: ExecuteQueryUseCase.SnapshotContext): List<String> {
        val sqls = mutableListOf<String>()

        val aliases = context.snapshots.map { it.alias }
        val duplicates = aliases.groupBy { it }.filter { it.value.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate aliases: ${duplicates.joinToString()}" }

        data class ResolvedTable(val tableName: String, val uri: String)
        val resolvedSnapshots = mutableListOf<List<ResolvedTable>>()

        for (entry in context.snapshots) {
            validateAlias(entry.alias)
            val snapshot = loadSnapshotPort.findById(entry.snapshotId)
                ?: throw SnapshotNotFoundException(entry.snapshotId)

            val tables = snapshot.tables.map { table ->
                val uri = storageProvider.getUri(table.storagePath)
                requireSafeUri(uri)
                ResolvedTable(table.table, uri)
            }
            resolvedSnapshots.add(tables)

            sqls.add("""CREATE SCHEMA "${entry.alias}"""")
            for (table in tables) {
                sqls.add("""CREATE VIEW "${entry.alias}"."${table.tableName}" AS SELECT * FROM '${table.uri}'""")
            }
        }

        // 단일 스냅샷이면 루트 뷰도 생성 (prefix 없이 접근 가능)
        if (resolvedSnapshots.size == 1) {
            for (table in resolvedSnapshots[0]) {
                sqls.add("""CREATE VIEW "${table.tableName}" AS SELECT * FROM '${table.uri}'""")
            }
        }

        return sqls
    }

    companion object {
        private val ALIAS_REGEX = Regex("^[a-z][a-z0-9_]{0,62}$")
        private val RESERVED_ALIASES = setOf("main", "information_schema", "temp", "pg_catalog")

        private fun validateAlias(alias: String) {
            require(ALIAS_REGEX.matches(alias)) {
                "Alias '$alias' must start with a lowercase letter and contain only lowercase letters, digits, and underscores"
            }
            require(alias !in RESERVED_ALIASES) {
                "Alias '$alias' is a reserved schema name"
            }
        }

        private fun requireSafeUri(uri: String) {
            require("'" !in uri) { "Storage URI contains invalid characters: $uri" }
        }
    }
}
