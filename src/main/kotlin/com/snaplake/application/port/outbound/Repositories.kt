package com.snaplake.application.port.outbound

import com.snaplake.domain.model.*
import com.snaplake.domain.vo.*

interface SaveUserPort {
    fun save(user: User): User
}

interface LoadUserPort {
    fun findByUsername(username: String): User?
    fun existsAny(): Boolean
}

interface SaveDatasourcePort {
    fun save(datasource: Datasource): Datasource
    fun deleteById(id: DatasourceId)
}

interface LoadDatasourcePort {
    fun findById(id: DatasourceId): Datasource?
    fun findAll(): List<Datasource>
    fun findAllEnabled(): List<Datasource>
}

interface SaveStorageConfigPort {
    fun save(config: StorageConfig): StorageConfig
}

interface LoadStorageConfigPort {
    fun find(): StorageConfig?
}

interface SaveSnapshotPort {
    fun save(snapshot: SnapshotMeta): SnapshotMeta
}

interface LoadSnapshotPort {
    fun findById(id: SnapshotId): SnapshotMeta?
    fun findByDatasourceId(datasourceId: DatasourceId): List<SnapshotMeta>
    fun findByDatasourceIdAndStatus(datasourceId: DatasourceId, status: SnapshotStatus): SnapshotMeta?
    fun findAll(): List<SnapshotMeta>
    fun deleteById(id: SnapshotId)
}
