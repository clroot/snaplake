package io.clroot.snaplake.adapter.outbound.persistence.adapter

import io.clroot.snaplake.adapter.outbound.persistence.mapper.DatasourceMapper
import io.clroot.snaplake.adapter.outbound.persistence.mapper.SnapshotMapper
import io.clroot.snaplake.adapter.outbound.persistence.mapper.StorageConfigMapper
import io.clroot.snaplake.adapter.outbound.persistence.mapper.UserMapper
import io.clroot.snaplake.adapter.outbound.persistence.repository.DatasourceJpaRepository
import io.clroot.snaplake.adapter.outbound.persistence.repository.SnapshotJpaRepository
import io.clroot.snaplake.adapter.outbound.persistence.repository.StorageConfigJpaRepository
import io.clroot.snaplake.adapter.outbound.persistence.repository.UserJpaRepository
import io.clroot.snaplake.application.port.outbound.*
import io.clroot.snaplake.domain.model.*
import io.clroot.snaplake.domain.vo.DatasourceId
import io.clroot.snaplake.domain.vo.SnapshotId
import org.springframework.stereotype.Component

@Component
class UserPersistenceAdapter(
    private val jpaRepository: UserJpaRepository,
    private val mapper: UserMapper,
) : SaveUserPort,
    LoadUserPort {
    override fun save(user: User): User {
        val entity = mapper.toEntity(user)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun findByUsername(username: String): User? = jpaRepository.findByUsername(username)?.let { mapper.toDomain(it) }

    override fun existsAny(): Boolean = jpaRepository.count() > 0
}

@Component
class DatasourcePersistenceAdapter(
    private val jpaRepository: DatasourceJpaRepository,
    private val mapper: DatasourceMapper,
) : SaveDatasourcePort,
    LoadDatasourcePort {
    override fun save(datasource: Datasource): Datasource {
        val entity = mapper.toEntity(datasource)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun deleteById(id: DatasourceId) {
        jpaRepository.deleteById(id.value)
    }

    override fun findById(id: DatasourceId): Datasource? = jpaRepository.findById(id.value).orElse(null)?.let { mapper.toDomain(it) }

    override fun findAll(): List<Datasource> = jpaRepository.findAll().map { mapper.toDomain(it) }

    override fun findAllEnabled(): List<Datasource> = jpaRepository.findAllByEnabled(1).map { mapper.toDomain(it) }
}

@Component
class StorageConfigPersistenceAdapter(
    private val jpaRepository: StorageConfigJpaRepository,
    private val mapper: StorageConfigMapper,
) : SaveStorageConfigPort,
    LoadStorageConfigPort {
    override fun save(config: StorageConfig): StorageConfig {
        val entity = mapper.toEntity(config)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun find(): StorageConfig? = jpaRepository.findById(1).orElse(null)?.let { mapper.toDomain(it) }
}

@Component
class SnapshotPersistenceAdapter(
    private val jpaRepository: SnapshotJpaRepository,
    private val mapper: SnapshotMapper,
    private val writeTransaction: SQLiteWriteTransaction,
) : SaveSnapshotPort,
    LoadSnapshotPort {
    override fun save(snapshot: SnapshotMeta): SnapshotMeta =
        writeTransaction.write("save snapshot ${snapshot.id.value}") {
            val entity = mapper.toEntity(snapshot)
            val saved = jpaRepository.save(entity)
            mapper.toDomain(saved)
        }

    override fun findById(id: SnapshotId): SnapshotMeta? = jpaRepository.findById(id.value).orElse(null)?.let { mapper.toDomain(it) }

    override fun findByDatasourceId(datasourceId: DatasourceId): List<SnapshotMeta> =
        jpaRepository.findAllByDatasourceId(datasourceId.value).map {
            mapper.toDomain(it)
        }

    override fun findByDatasourceIdAndStatus(
        datasourceId: DatasourceId,
        status: SnapshotStatus,
    ): SnapshotMeta? =
        jpaRepository
            .findByDatasourceIdAndStatus(datasourceId.value, status.name)
            ?.let { mapper.toDomain(it) }

    override fun findAllByStatus(status: SnapshotStatus): List<SnapshotMeta> =
        jpaRepository.findAllByStatus(status.name).map { mapper.toDomain(it) }

    override fun findAll(): List<SnapshotMeta> = jpaRepository.findAll().map { mapper.toDomain(it) }

    override fun deleteById(id: SnapshotId) {
        jpaRepository.deleteById(id.value)
    }
}
