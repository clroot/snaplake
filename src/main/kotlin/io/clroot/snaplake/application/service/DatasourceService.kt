package io.clroot.snaplake.application.service

import io.clroot.snaplake.application.port.inbound.*
import io.clroot.snaplake.application.port.outbound.*
import io.clroot.snaplake.domain.exception.DatasourceNotFoundException
import io.clroot.snaplake.domain.model.Datasource
import io.clroot.snaplake.domain.vo.DatasourceId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class DatasourceService(
    private val saveDatasourcePort: SaveDatasourcePort,
    private val loadDatasourcePort: LoadDatasourcePort,
    private val encryptionPort: EncryptionPort,
    private val dialectRegistry: DatabaseDialectRegistry,
    private val snapshotSchedulerPort: SnapshotSchedulerPort,
) : RegisterDatasourceUseCase,
    UpdateDatasourceUseCase,
    DeleteDatasourceUseCase,
    GetDatasourceUseCase,
    TestDatasourceConnectionUseCase,
    ListDatasourceTablesUseCase {
    override fun register(command: RegisterDatasourceUseCase.Command): Datasource {
        val encryptedPassword = encryptionPort.encrypt(command.password)
        val datasource =
            Datasource.create(
                name = command.name,
                type = command.type,
                host = command.host,
                port = command.port,
                database = command.database,
                username = command.username,
                encryptedPassword = encryptedPassword,
                schemas = command.schemas,
                cronExpression = command.cronExpression,
                retentionPolicy = command.retentionPolicy,
                includedTables = command.includedTables,
            )
        val saved = saveDatasourcePort.save(datasource)
        snapshotSchedulerPort.register(saved)
        return saved
    }

    override fun update(
        id: DatasourceId,
        command: UpdateDatasourceUseCase.Command,
    ): Datasource {
        val existing =
            loadDatasourcePort.findById(id)
                ?: throw DatasourceNotFoundException(id)

        require(command.name.isNotBlank()) { "Datasource name must not be blank" }
        require(!command.name.contains("..") && !command.name.contains('/') && !command.name.contains('\\')) {
            "Datasource name must not contain path characters (/, \\, ..)"
        }
        require(command.host.isNotBlank()) { "Host must not be blank" }
        require(command.port in 1..65535) { "Port must be between 1 and 65535" }
        require(command.database.isNotBlank()) { "Database name must not be blank" }
        require(command.username.isNotBlank()) { "Username must not be blank" }
        require(command.schemas.isNotEmpty()) { "At least one schema is required" }

        val encryptedPassword =
            if (command.password != null) {
                encryptionPort.encrypt(command.password)
            } else {
                existing.encryptedPassword
            }

        val updated =
            Datasource.reconstitute(
                id = existing.id,
                name = command.name,
                type = command.type,
                host = command.host,
                port = command.port,
                database = command.database,
                username = command.username,
                encryptedPassword = encryptedPassword,
                schemas = command.schemas,
                cronExpression = command.cronExpression,
                retentionPolicy = command.retentionPolicy,
                includedTables = command.includedTables,
                enabled = existing.enabled,
                createdAt = existing.createdAt,
                updatedAt = java.time.Instant.now(),
            )
        val saved = saveDatasourcePort.save(updated)
        snapshotSchedulerPort.reschedule(saved)
        return saved
    }

    override fun delete(id: DatasourceId) {
        val existing =
            loadDatasourcePort.findById(id)
                ?: throw DatasourceNotFoundException(id)
        snapshotSchedulerPort.unregister(existing.id)
        saveDatasourcePort.deleteById(existing.id)
    }

    @Transactional(readOnly = true)
    override fun getById(id: DatasourceId): Datasource =
        loadDatasourcePort.findById(id)
            ?: throw DatasourceNotFoundException(id)

    @Transactional(readOnly = true)
    override fun getAll(): List<Datasource> = loadDatasourcePort.findAll()

    @Transactional(readOnly = true)
    override fun test(id: DatasourceId): ConnectionTestResult {
        val datasource =
            loadDatasourcePort.findById(id)
                ?: throw DatasourceNotFoundException(id)

        val dialect = dialectRegistry.getDialect(datasource.type)
        val decryptedPassword = encryptionPort.decrypt(datasource.encryptedPassword)
        return dialect.testConnection(datasource, decryptedPassword)
    }

    @Transactional(readOnly = true)
    override fun listTables(datasourceId: DatasourceId): Map<String, List<String>> {
        val datasource =
            loadDatasourcePort.findById(datasourceId)
                ?: throw DatasourceNotFoundException(datasourceId)

        val dialect = dialectRegistry.getDialect(datasource.type)
        val decryptedPassword = encryptionPort.decrypt(datasource.encryptedPassword)

        return dialect.createConnection(datasource, decryptedPassword).use { conn ->
            datasource.schemas.associateWith { schema ->
                dialect.listTables(conn, schema).map { it.name }
            }
        }
    }
}
