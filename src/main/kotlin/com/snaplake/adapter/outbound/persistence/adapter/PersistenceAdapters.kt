package com.snaplake.adapter.outbound.persistence.adapter

import com.snaplake.adapter.outbound.persistence.mapper.*
import com.snaplake.adapter.outbound.persistence.repository.*
import com.snaplake.application.port.outbound.*
import com.snaplake.domain.model.*
import com.snaplake.domain.vo.*
import org.springframework.stereotype.Component

@Component
class UserPersistenceAdapter(
    private val jpaRepository: UserJpaRepository,
    private val mapper: UserMapper,
) : SaveUserPort, LoadUserPort {

    override fun save(user: User): User {
        val entity = mapper.toEntity(user)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun findByUsername(username: String): User? {
        return jpaRepository.findByUsername(username)?.let { mapper.toDomain(it) }
    }

    override fun existsAny(): Boolean {
        return jpaRepository.count() > 0
    }
}

@Component
class DatasourcePersistenceAdapter(
    private val jpaRepository: DatasourceJpaRepository,
    private val mapper: DatasourceMapper,
) : SaveDatasourcePort, LoadDatasourcePort {

    override fun save(datasource: Datasource): Datasource {
        val entity = mapper.toEntity(datasource)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun deleteById(id: DatasourceId) {
        jpaRepository.deleteById(id.value)
    }

    override fun findById(id: DatasourceId): Datasource? {
        return jpaRepository.findById(id.value).orElse(null)?.let { mapper.toDomain(it) }
    }

    override fun findAll(): List<Datasource> {
        return jpaRepository.findAll().map { mapper.toDomain(it) }
    }

    override fun findAllEnabled(): List<Datasource> {
        return jpaRepository.findAllByEnabled(1).map { mapper.toDomain(it) }
    }
}

@Component
class StorageConfigPersistenceAdapter(
    private val jpaRepository: StorageConfigJpaRepository,
    private val mapper: StorageConfigMapper,
) : SaveStorageConfigPort, LoadStorageConfigPort {

    override fun save(config: StorageConfig): StorageConfig {
        val entity = mapper.toEntity(config)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun find(): StorageConfig? {
        return jpaRepository.findById(1).orElse(null)?.let { mapper.toDomain(it) }
    }
}

@Component
class SnapshotPersistenceAdapter(
    private val jpaRepository: SnapshotJpaRepository,
    private val mapper: SnapshotMapper,
) : SaveSnapshotPort, LoadSnapshotPort {

    override fun save(snapshot: SnapshotMeta): SnapshotMeta {
        val entity = mapper.toEntity(snapshot)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun findById(id: SnapshotId): SnapshotMeta? {
        return jpaRepository.findById(id.value).orElse(null)?.let { mapper.toDomain(it) }
    }

    override fun findByDatasourceId(datasourceId: DatasourceId): List<SnapshotMeta> {
        return jpaRepository.findAllByDatasourceId(datasourceId.value).map { mapper.toDomain(it) }
    }

    override fun findByDatasourceIdAndStatus(datasourceId: DatasourceId, status: SnapshotStatus): SnapshotMeta? {
        return jpaRepository.findByDatasourceIdAndStatus(datasourceId.value, status.name)
            ?.let { mapper.toDomain(it) }
    }

    override fun findAll(): List<SnapshotMeta> {
        return jpaRepository.findAll().map { mapper.toDomain(it) }
    }

    override fun deleteById(id: SnapshotId) {
        jpaRepository.deleteById(id.value)
    }
}
