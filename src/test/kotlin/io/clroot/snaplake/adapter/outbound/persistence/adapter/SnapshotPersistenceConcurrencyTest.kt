package io.clroot.snaplake.adapter.outbound.persistence.adapter

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.clroot.snaplake.application.port.outbound.*
import io.clroot.snaplake.application.service.SnapshotService
import io.clroot.snaplake.domain.exception.SnapshotAlreadyRunningException
import io.clroot.snaplake.domain.model.*
import io.clroot.snaplake.domain.vo.DatasourceId
import io.clroot.snaplake.domain.vo.SnapshotId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.nio.file.Files
import java.sql.Connection
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

// Exercise the real JPA adapters and SQLite connections independently of schema migrations.
@SpringBootTest(
    properties = [
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
@DirtiesContext
class SnapshotPersistenceConcurrencyTest {
    @Autowired
    lateinit var metadataDataSource: DataSource

    @Autowired
    lateinit var datasourcePersistence: DatasourcePersistenceAdapter

    @Autowired
    lateinit var snapshotPersistence: SnapshotPersistenceAdapter

    private lateinit var dialect: DatabaseDialect
    private lateinit var storage: StorageProvider
    private lateinit var service: SnapshotService

    @BeforeEach
    fun setUp() {
        snapshotPersistence.findAll().forEach { snapshotPersistence.deleteById(it.id) }
        datasourcePersistence.findAll().forEach { datasourcePersistence.deleteById(it.id) }

        dialect = mockk()
        storage = mockk(relaxed = true)
        val registry = mockk<DatabaseDialectRegistry>()
        val encryption = mockk<EncryptionPort>()
        val parquetWriter = mockk<ParquetWritePort>()
        every { registry.getDialect(DatabaseType.POSTGRESQL) } returns dialect
        every { encryption.decrypt(any()) } returns "password"
        every { dialect.createConnection(any(), any()) } answers { mockk<Connection>(relaxed = true) }
        every { dialect.listTables(any(), "public") } returns listOf(TableInfo("public", "users"))
        every { dialect.listPrimaryKeys(any(), any(), any()) } returns listOf("id")
        every { parquetWriter.writeResultSetToParquet(any()) } returns ParquetWriteResult(byteArrayOf(1, 2, 3), 1)
        service = SnapshotService(datasourcePersistence, snapshotPersistence, snapshotPersistence, storage, registry, encryption, parquetWriter)
    }

    @Test
    fun `every pooled connection uses WAL and a finite busy timeout`() {
        val connections = List(4) { metadataDataSource.connection }
        try {
            connections.forEach { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA journal_mode").use {
                        it.next() shouldBe true
                        it.getString(1) shouldBe "wal"
                    }
                    statement.executeQuery("PRAGMA busy_timeout").use {
                        it.next() shouldBe true
                        it.getInt(1) shouldBe 1000
                    }
                }
            }
        } finally {
            connections.forEach { it.close() }
        }
    }

    @Test
    fun `initial insert recovers after an actual lock failure and rejects overlapping requests`() {
        val datasource = createDatasource()
        Executors.newSingleThreadExecutor().use { executor ->
            RetryObserver().use { retries ->
                val pending = withWriteLock {
                    val future = executor.submit<SnapshotMeta> { service.takeSnapshot(datasource.id) }
                    retries.awaitRetry()
                    verify(exactly = 0) { dialect.createConnection(any(), any()) }
                    shouldThrow<SnapshotAlreadyRunningException> { service.takeSnapshot(datasource.id) }
                    future
                }

                val snapshot = pending.get(10, TimeUnit.SECONDS)
                snapshot.status shouldBe SnapshotStatus.COMPLETED
                val persisted = dailySnapshots(datasource.id)
                persisted.size shouldBe 1
                persisted.single().id shouldBe snapshot.id
                persisted.single().tables.size shouldBe 1
                verify(exactly = 1) { dialect.createConnection(any(), any()) }
            }
        }
    }

    @Test
    fun `persistent lock exhausts retries without running a dump and releases the execution guard`() {
        val datasource = createDatasource()
        Executors.newSingleThreadExecutor().use { executor ->
            RetryObserver().use { retries ->
                withWriteLock {
                    val pending = executor.submit<SnapshotMeta> { service.takeSnapshot(datasource.id) }
                    shouldThrow<ExecutionException> { pending.get(20, TimeUnit.SECONDS) }
                    // Five attempts for RUNNING, then five for the best-effort FAILED record.
                    retries.retryCount.get() shouldBe 8
                    snapshotPersistence.findByDatasourceId(datasource.id).size shouldBe 0
                    verify(exactly = 0) { dialect.createConnection(any(), any()) }
                }
            }
        }

        service.takeSnapshot(datasource.id).status shouldBe SnapshotStatus.COMPLETED
        dailySnapshots(datasource.id).size shouldBe 1
    }

    @Test
    fun `different datasources can dump concurrently after their metadata writes contend`() {
        val first = createDatasource()
        val second = createDatasource()
        val bothDumping = CountDownLatch(2)
        every { dialect.createConnection(any(), any()) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            bothDumping.countDown()
            check(bothDumping.await(10, TimeUnit.SECONDS)) { "Metadata transaction blocked another snapshot's dump" }
            mockk<Connection>(relaxed = true)
        }
        every { storage.write(any(), any()) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
        }

        Executors.newFixedThreadPool(2).use { executor ->
            RetryObserver(expectedRetries = 2).use { retries ->
                val pending = withWriteLock {
                    val futures = listOf(first, second).map { datasource ->
                        executor.submit<SnapshotMeta> { service.takeSnapshot(datasource.id) }
                    }
                    retries.awaitRetry()
                    futures
                }
                pending.forEach { it.get(15, TimeUnit.SECONDS).status shouldBe SnapshotStatus.COMPLETED }
            }
        }

        dailySnapshots(first.id).size shouldBe 1
        dailySnapshots(second.id).size shouldBe 1
        verify(exactly = 2) { dialect.createConnection(any(), any()) }
    }

    @Test
    fun `retrying completed metadata preserves one snapshot and one copy of each table`() {
        val datasource = createDatasource()
        val snapshot = SnapshotMeta.start(datasource.id, datasource.name, SnapshotType.DAILY, LocalDate.now())
        snapshotPersistence.save(snapshot)
        snapshot.addTable(TableMeta("public", "users", 3, 100, "users.parquet", listOf("id")))
        snapshot.complete()

        Executors.newSingleThreadExecutor().use { executor ->
            RetryObserver().use { retries ->
                val pending = withWriteLock {
                    val future = executor.submit<SnapshotMeta> { snapshotPersistence.save(snapshot) }
                    retries.awaitRetry()
                    future
                }
                pending.get(10, TimeUnit.SECONDS).status shouldBe SnapshotStatus.COMPLETED
            }
        }

        val persisted = snapshotPersistence.findByDatasourceId(datasource.id).single()
        persisted.id shouldBe snapshot.id
        persisted.status shouldBe SnapshotStatus.COMPLETED
        persisted.tables shouldBe listOf(TableMeta("public", "users", 3, 100, "users.parquet", listOf("id")))
    }

    @Test
    fun `stale and startup recovery still persist failed snapshots`() {
        val datasource = createDatasource()
        val stale = SnapshotMeta.reconstitute(
            id = SnapshotId.generate(),
            datasourceId = datasource.id,
            datasourceName = datasource.name,
            snapshotType = SnapshotType.DAILY,
            snapshotDate = LocalDate.now(),
            startedAt = Instant.now().minus(Duration.ofHours(1)),
            status = SnapshotStatus.RUNNING,
            completedAt = null,
            errorMessage = null,
            tables = emptyList(),
        )
        val recent = SnapshotMeta.start(datasource.id, datasource.name, SnapshotType.DAILY, LocalDate.now())
        snapshotPersistence.save(stale)
        snapshotPersistence.save(recent)

        service.recoverStale() shouldBe 1
        snapshotPersistence.findById(stale.id)!!.status shouldBe SnapshotStatus.FAILED
        snapshotPersistence.findById(recent.id)!!.status shouldBe SnapshotStatus.RUNNING
        service.recoverAll() shouldBe 1
        snapshotPersistence.findById(recent.id)!!.status shouldBe SnapshotStatus.FAILED
    }

    private fun dailySnapshots(datasourceId: DatasourceId): List<SnapshotMeta> =
        snapshotPersistence.findByDatasourceId(datasourceId).filter { it.snapshotType == SnapshotType.DAILY }

    private fun <T> withWriteLock(action: () -> T): T =
        metadataDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("BEGIN IMMEDIATE")
                try {
                    action()
                } finally {
                    statement.execute("ROLLBACK")
                }
            }
        }

    private fun createDatasource(): Datasource {
        val id = DatasourceId.generate()
        return datasourcePersistence.save(
            Datasource.reconstitute(
                id = id,
                name = "test-${id.value}",
                type = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                database = "test",
                username = "user",
                encryptedPassword = "encrypted",
                schemas = listOf("public"),
                cronExpression = null,
                retentionPolicy = RetentionPolicy(),
                enabled = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )
    }

    private class RetryObserver(expectedRetries: Int = 1) : AppenderBase<ILoggingEvent>(), AutoCloseable {
        private val logger = LoggerFactory.getLogger(SQLiteWriteTransaction::class.java) as Logger
        private val retried = CountDownLatch(expectedRetries)
        val retryCount = AtomicInteger()

        init {
            start()
            logger.addAppender(this)
        }

        override fun append(event: ILoggingEvent) {
            if (event.level == Level.WARN && event.message.startsWith("event=sqlite_write_retry ")) {
                retryCount.incrementAndGet()
                retried.countDown()
            }
        }

        fun awaitRetry() {
            check(retried.await(10, TimeUnit.SECONDS)) { "Expected a real SQLite lock failure before releasing the lock" }
        }

        override fun close() {
            logger.detachAppender(this)
            stop()
        }
    }

    companion object {
        private val dataDirectory = Files.createTempDirectory("snaplake-concurrency-test-")

        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { "jdbc:sqlite:${dataDirectory.resolve("metadata.db")}" }
            registry.add("snaplake.data-dir") { dataDirectory.toString() }
        }
    }
}
