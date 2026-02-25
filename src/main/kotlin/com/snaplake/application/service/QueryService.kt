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
        return queryEngine.executeQuery(
            sql = command.sql,
            storageConfig = storageConfig,
            limit = command.limit,
            offset = command.offset,
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
}
