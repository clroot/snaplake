package com.snaplake.application.service

import com.snaplake.application.port.outbound.*
import com.snaplake.domain.exception.DatasourceNotFoundException
import com.snaplake.domain.exception.SnapshotAlreadyRunningException
import com.snaplake.domain.model.*
import com.snaplake.domain.vo.DatasourceId
import com.snaplake.domain.vo.SnapshotId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.time.Instant

class SnapshotServiceTest : DescribeSpec({

    val loadDatasourcePort = mockk<LoadDatasourcePort>()
    val saveSnapshotPort = mockk<SaveSnapshotPort>(relaxed = true)
    val loadSnapshotPort = mockk<LoadSnapshotPort>()
    val storageProvider = mockk<StorageProvider>(relaxed = true)
    val dialectRegistry = mockk<DatabaseDialectRegistry>()
    val encryptionPort = mockk<EncryptionPort>()
    val parquetWritePort = mockk<ParquetWritePort>()

    val sut = SnapshotService(
        loadDatasourcePort, saveSnapshotPort, loadSnapshotPort,
        storageProvider, dialectRegistry, encryptionPort, parquetWritePort,
    )

    beforeTest {
        clearAllMocks()
        every { saveSnapshotPort.save(any()) } answers { firstArg() }
        every { loadSnapshotPort.findByDatasourceId(any()) } returns emptyList()
    }

    describe("takeSnapshot") {
        val datasourceId = DatasourceId.generate()
        val datasource = Datasource.reconstitute(
            id = datasourceId,
            name = "test-db",
            type = DatabaseType.POSTGRESQL,
            host = "localhost",
            port = 5432,
            database = "mydb",
            username = "user",
            encryptedPassword = "encrypted",
            schemas = listOf("public"),
            cronExpression = null,
            retentionPolicy = RetentionPolicy(),
            enabled = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

        context("존재하지 않는 datasource인 경우") {
            it("DatasourceNotFoundException을 던진다") {
                every { loadDatasourcePort.findById(datasourceId) } returns null

                shouldThrow<DatasourceNotFoundException> {
                    sut.takeSnapshot(datasourceId)
                }
            }
        }

        context("이미 RUNNING 중인 스냅샷이 있는 경우") {
            it("SnapshotAlreadyRunningException을 던진다") {
                every { loadDatasourcePort.findById(datasourceId) } returns datasource
                every { loadSnapshotPort.findByDatasourceIdAndStatus(datasourceId, SnapshotStatus.RUNNING) } returns
                    SnapshotMeta.start(datasourceId, "test-db", SnapshotType.DAILY, java.time.LocalDate.now())

                shouldThrow<SnapshotAlreadyRunningException> {
                    sut.takeSnapshot(datasourceId)
                }
            }
        }

        context("연결 실패인 경우") {
            it("FAILED 상태로 저장한다") {
                val dialect = mockk<DatabaseDialect>()
                every { loadDatasourcePort.findById(datasourceId) } returns datasource
                every { loadSnapshotPort.findByDatasourceIdAndStatus(datasourceId, SnapshotStatus.RUNNING) } returns null
                every { encryptionPort.decrypt("encrypted") } returns "password"
                every { dialectRegistry.getDialect(DatabaseType.POSTGRESQL) } returns dialect
                every { dialect.createConnection(datasource, "password") } throws RuntimeException("Connection refused")

                val result = sut.takeSnapshot(datasourceId)

                result.status shouldBe SnapshotStatus.FAILED
                result.errorMessage shouldBe "Connection failed: Connection refused"
            }
        }

        context("정상적인 스냅샷인 경우") {
            it("테이블을 Parquet로 변환하여 저장한다") {
                val dialect = mockk<DatabaseDialect>()
                val conn = mockk<Connection>()
                val stmt = mockk<Statement>()
                val rs = mockk<ResultSet>()

                every { loadDatasourcePort.findById(datasourceId) } returns datasource
                every { loadSnapshotPort.findByDatasourceIdAndStatus(datasourceId, SnapshotStatus.RUNNING) } returns null
                every { encryptionPort.decrypt("encrypted") } returns "password"
                every { dialectRegistry.getDialect(DatabaseType.POSTGRESQL) } returns dialect
                every { dialect.createConnection(datasource, "password") } returns conn
                every { dialect.listTables(conn, "public") } returns listOf(TableInfo("public", "users"))
                every { conn.createStatement() } returns stmt

                every { stmt.executeQuery(match { it.contains("SELECT *") }) } returns rs
                every { rs.close() } just Runs
                every { parquetWritePort.writeResultSetToParquet(rs) } returns
                    ParquetWriteResult(data = byteArrayOf(1, 2, 3), rowCount = 1)

                every { conn.close() } just Runs
                every { storageProvider.write(any(), any()) } just Runs

                val result = sut.takeSnapshot(datasourceId)

                result.status shouldBe SnapshotStatus.COMPLETED
                result.tables.size shouldBe 1
                result.tables[0].table shouldBe "users"
                result.tables[0].rowCount shouldBe 1
                result.tables[0].sizeBytes shouldBe 3L
                verify { storageProvider.write(any(), any()) }
            }
        }
    }

    describe("delete") {
        context("존재하지 않는 snapshot인 경우") {
            it("SnapshotNotFoundException을 던진다") {
                val id = SnapshotId.generate()
                every { loadSnapshotPort.findById(id) } returns null

                shouldThrow<com.snaplake.domain.exception.SnapshotNotFoundException> {
                    sut.delete(id)
                }
            }
        }
    }
})
