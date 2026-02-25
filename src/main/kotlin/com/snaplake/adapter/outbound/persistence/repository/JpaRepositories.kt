package com.snaplake.adapter.outbound.persistence.repository

import com.snaplake.adapter.outbound.persistence.entity.*
import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<UserEntity, String> {
    fun findByUsername(username: String): UserEntity?
}

interface DatasourceJpaRepository : JpaRepository<DatasourceEntity, String> {
    fun findAllByEnabled(enabled: Int): List<DatasourceEntity>
}

interface StorageConfigJpaRepository : JpaRepository<StorageConfigEntity, Int>

interface SnapshotJpaRepository : JpaRepository<SnapshotEntity, String> {
    fun findAllByDatasourceId(datasourceId: String): List<SnapshotEntity>
    fun findByDatasourceIdAndStatus(datasourceId: String, status: String): SnapshotEntity?
}

interface SnapshotTableJpaRepository : JpaRepository<SnapshotTableEntity, String>
