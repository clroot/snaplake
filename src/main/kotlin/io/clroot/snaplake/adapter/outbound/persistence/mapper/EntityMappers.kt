package io.clroot.snaplake.adapter.outbound.persistence.mapper

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.clroot.snaplake.adapter.outbound.persistence.entity.*
import io.clroot.snaplake.application.port.outbound.EncryptionPort
import io.clroot.snaplake.domain.model.*
import io.clroot.snaplake.domain.vo.DatasourceId
import io.clroot.snaplake.domain.vo.SnapshotId
import io.clroot.snaplake.domain.vo.UserId
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

private val objectMapper = jacksonObjectMapper()

@Component
class UserMapper {
    fun toEntity(user: User): UserEntity =
        UserEntity(
            id = user.id.value,
            username = user.username,
            passwordHash = user.passwordHash,
            role = user.role.name,
            createdAt = user.createdAt.toString(),
        )

    fun toDomain(entity: UserEntity): User =
        User.reconstitute(
            id = UserId(entity.id),
            username = entity.username,
            passwordHash = entity.passwordHash,
            role = UserRole.valueOf(entity.role),
            createdAt = Instant.parse(entity.createdAt),
        )
}

@Component
class DatasourceMapper {
    fun toEntity(ds: Datasource): DatasourceEntity =
        DatasourceEntity(
            id = ds.id.value,
            name = ds.name,
            type = ds.type.name,
            host = ds.host,
            port = ds.port,
            databaseName = ds.database,
            username = ds.username,
            encryptedPassword = ds.encryptedPassword,
            schemas = objectMapper.writeValueAsString(ds.schemas),
            cronExpression = ds.cronExpression,
            retentionDaily = ds.retentionPolicy.dailyMaxCount,
            retentionMonthly = ds.retentionPolicy.monthlyMaxCount,
            includedTables = objectMapper.writeValueAsString(ds.includedTables),
            enabled = if (ds.enabled) 1 else 0,
            createdAt = ds.createdAt.toString(),
            updatedAt = ds.updatedAt.toString(),
        )

    fun toDomain(entity: DatasourceEntity): Datasource =
        Datasource.reconstitute(
            id = DatasourceId(entity.id),
            name = entity.name,
            type = DatabaseType.valueOf(entity.type),
            host = entity.host,
            port = entity.port,
            database = entity.databaseName,
            username = entity.username,
            encryptedPassword = entity.encryptedPassword,
            schemas = objectMapper.readValue(entity.schemas, object : TypeReference<List<String>>() {}),
            cronExpression = entity.cronExpression,
            retentionPolicy =
                RetentionPolicy(
                    dailyMaxCount = entity.retentionDaily,
                    monthlyMaxCount = entity.retentionMonthly,
                ),
            includedTables = entity.includedTables?.let {
                objectMapper.readValue(it, object : TypeReference<Map<String, List<String>>>() {})
            } ?: emptyMap(),
            enabled = entity.enabled == 1,
            createdAt = Instant.parse(entity.createdAt),
            updatedAt = Instant.parse(entity.updatedAt),
        )
}

@Component
class StorageConfigMapper(
    private val encryptionPort: EncryptionPort,
) {
    companion object {
        internal const val ENCRYPTED_PREFIX = "ENC:"
    }

    fun toEntity(config: StorageConfig): StorageConfigEntity =
        StorageConfigEntity(
            id = 1,
            type = config.type.name,
            localPath = config.localPath,
            s3Bucket = config.s3Bucket,
            s3Region = config.s3Region,
            s3Endpoint = config.s3Endpoint,
            s3AccessKey = config.s3AccessKey?.let { ENCRYPTED_PREFIX + encryptionPort.encrypt(it) },
            s3SecretKey = config.s3SecretKey?.let { ENCRYPTED_PREFIX + encryptionPort.encrypt(it) },
            updatedAt = Instant.now().toString(),
        )

    fun toDomain(entity: StorageConfigEntity): StorageConfig =
        StorageConfig.reconstitute(
            type = StorageType.valueOf(entity.type),
            localPath = entity.localPath,
            s3Bucket = entity.s3Bucket,
            s3Region = entity.s3Region,
            s3Endpoint = entity.s3Endpoint,
            s3AccessKey = entity.s3AccessKey?.let { decryptIfNeeded(it) },
            s3SecretKey = entity.s3SecretKey?.let { decryptIfNeeded(it) },
        )

    private fun decryptIfNeeded(value: String): String =
        if (value.startsWith(ENCRYPTED_PREFIX)) {
            encryptionPort.decrypt(value.removePrefix(ENCRYPTED_PREFIX))
        } else {
            value
        }
}

@Component
class SnapshotMapper {
    fun toEntity(snapshot: SnapshotMeta): SnapshotEntity {
        val entity =
            SnapshotEntity(
                id = snapshot.id.value,
                datasourceId = snapshot.datasourceId.value,
                datasourceName = snapshot.datasourceName,
                snapshotType = snapshot.snapshotType.name,
                snapshotDate = snapshot.snapshotDate.toString(),
                status = snapshot.status.name,
                startedAt = snapshot.startedAt.toString(),
                completedAt = snapshot.completedAt?.toString(),
                errorMessage = snapshot.errorMessage,
                tags = objectMapper.writeValueAsString(snapshot.tags),
                memo = snapshot.memo,
            )
        snapshot.tables.forEach { table ->
            entity.tables.add(
                SnapshotTableEntity(
                    snapshot = entity,
                    schemaName = table.schema,
                    tableName = table.table,
                    rowCount = table.rowCount,
                    sizeBytes = table.sizeBytes,
                    storagePath = table.storagePath,
                    primaryKeys = objectMapper.writeValueAsString(table.primaryKeys),
                ),
            )
        }
        return entity
    }

    fun toDomain(entity: SnapshotEntity): SnapshotMeta =
        SnapshotMeta.reconstitute(
            id = SnapshotId(entity.id),
            datasourceId = DatasourceId(entity.datasourceId),
            datasourceName = entity.datasourceName,
            snapshotType = SnapshotType.valueOf(entity.snapshotType),
            snapshotDate = LocalDate.parse(entity.snapshotDate),
            startedAt = Instant.parse(entity.startedAt),
            status = SnapshotStatus.valueOf(entity.status),
            completedAt = entity.completedAt?.let { Instant.parse(it) },
            errorMessage = entity.errorMessage,
            tags = entity.tags?.let {
                objectMapper.readValue(it, object : TypeReference<List<String>>() {})
            } ?: emptyList(),
            memo = entity.memo,
            tables =
                entity.tables.map { tableEntity ->
                    TableMeta(
                        schema = tableEntity.schemaName,
                        table = tableEntity.tableName,
                        rowCount = tableEntity.rowCount,
                        sizeBytes = tableEntity.sizeBytes,
                        storagePath = tableEntity.storagePath,
                        primaryKeys = objectMapper.readValue(tableEntity.primaryKeys, object : TypeReference<List<String>>() {}),
                    )
                },
        )
}
