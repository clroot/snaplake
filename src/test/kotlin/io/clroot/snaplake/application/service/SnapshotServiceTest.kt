package io.clroot.snaplake.application.service

import io.clroot.snaplake.application.port.outbound.*
import io.clroot.snaplake.domain.exception.DatasourceNotFoundException
import io.clroot.snaplake.domain.exception.SnapshotAlreadyRunningException
import io.clroot.snaplake.domain.model.*
import io.clroot.snaplake.domain.vo.DatasourceId
import io.clroot.snaplake.domain.vo.SnapshotId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class SnapshotServiceTest :
    DescribeSpec({

        val loadDatasourcePort = mockk<LoadDatasourcePort>()
        val saveSnapshotPort = mockk<SaveSnapshotPort>(relaxed = true)
        val loadSnapshotPort = mockk<LoadSnapshotPort>()
        val storageProvider = mockk<StorageProvider>(relaxed = true)
        val dialectRegistry = mockk<DatabaseDialectRegistry>()
        val encryptionPort = mockk<EncryptionPort>()
        val parquetWritePort = mockk<ParquetWritePort>()

        val sut =
            SnapshotService(
                loadDatasourcePort,
                saveSnapshotPort,
                loadSnapshotPort,
                storageProvider,
                dialectRegistry,
                encryptionPort,
                parquetWritePort,
            )

        beforeTest {
            clearAllMocks()
            every { saveSnapshotPort.save(any()) } answers { firstArg() }
            every { loadSnapshotPort.findByDatasourceId(any()) } returns emptyList()
        }

        describe("takeSnapshot") {
            val datasourceId = DatasourceId.generate()
            val datasource =
                Datasource.reconstitute(
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

            context("최초 메타데이터 저장에 실패한 경우") {
                it("같은 ID로 FAILED를 기록하고 원래 예외를 전달한다") {
                    val failure = IllegalStateException("metadata unavailable")
                    val attempts = mutableListOf<Pair<SnapshotId, SnapshotStatus>>()
                    every { loadDatasourcePort.findById(datasourceId) } returns datasource
                    every { loadSnapshotPort.findByDatasourceIdAndStatus(datasourceId, SnapshotStatus.RUNNING) } returns null
                    every { saveSnapshotPort.save(any()) } answers {
                        val snapshot = firstArg<SnapshotMeta>()
                        attempts.add(snapshot.id to snapshot.status)
                        if (attempts.size == 1) throw failure
                        snapshot
                    }

                    shouldThrow<IllegalStateException> { sut.takeSnapshot(datasourceId) } shouldBe failure

                    attempts.map { it.second } shouldBe listOf(SnapshotStatus.RUNNING, SnapshotStatus.FAILED)
                    attempts.map { it.first }.distinct().size shouldBe 1
                    verify { dialectRegistry wasNot Called }
                }

                it("실패 상태 기록도 실패하면 최초 예외를 유지한다") {
                    val initialFailure = IllegalStateException("initial write failed")
                    every { loadDatasourcePort.findById(datasourceId) } returns datasource
                    every { loadSnapshotPort.findByDatasourceIdAndStatus(datasourceId, SnapshotStatus.RUNNING) } returns null
                    every { saveSnapshotPort.save(any()) } throws initialFailure andThenThrows RuntimeException("failure write failed")

                    shouldThrow<IllegalStateException> { sut.takeSnapshot(datasourceId) } shouldBe initialFailure
                    verify(exactly = 2) { saveSnapshotPort.save(any()) }
                    verify { dialectRegistry wasNot Called }
                }
            }

            context("최종 상태 저장에 실패한 경우") {
                it("연결 실패 기록이 저장되지 않으면 FAILED를 다시 저장한다") {
                    val savedStatuses = mutableListOf<SnapshotStatus>()
                    val dialect = mockk<DatabaseDialect>()
                    every { loadDatasourcePort.findById(datasourceId) } returns datasource
                    every { loadSnapshotPort.findByDatasourceIdAndStatus(datasourceId, SnapshotStatus.RUNNING) } returns null
                    every { encryptionPort.decrypt("encrypted") } returns "password"
                    every { dialectRegistry.getDialect(DatabaseType.POSTGRESQL) } returns dialect
                    every { dialect.createConnection(datasource, "password") } throws RuntimeException("Connection refused")
                    every { saveSnapshotPort.save(any()) } answers {
                        val snapshot = firstArg<SnapshotMeta>()
                        savedStatuses.add(snapshot.status)
                        if (savedStatuses.size == 2) throw IllegalStateException("metadata locked")
                        snapshot
                    }

                    shouldThrow<IllegalStateException> { sut.takeSnapshot(datasourceId) }

                    savedStatuses shouldBe listOf(SnapshotStatus.RUNNING, SnapshotStatus.FAILED, SnapshotStatus.FAILED)
                }

                it("완료 기록이 저장되지 않으면 COMPLETED를 다시 저장한다") {
                    val savedStatuses = mutableListOf<SnapshotStatus>()
                    val dialect = mockk<DatabaseDialect>()
                    val conn = mockk<Connection>(relaxed = true)
                    every { loadDatasourcePort.findById(datasourceId) } returns datasource
                    every { loadSnapshotPort.findByDatasourceIdAndStatus(datasourceId, SnapshotStatus.RUNNING) } returns null
                    every { encryptionPort.decrypt("encrypted") } returns "password"
                    every { dialectRegistry.getDialect(DatabaseType.POSTGRESQL) } returns dialect
                    every { dialect.createConnection(datasource, "password") } returns conn
                    every { dialect.listTables(conn, "public") } returns emptyList()
                    every { saveSnapshotPort.save(any()) } answers {
                        val snapshot = firstArg<SnapshotMeta>()
                        savedStatuses.add(snapshot.status)
                        if (savedStatuses.size == 2) throw IllegalStateException("metadata locked")
                        snapshot
                    }

                    shouldThrow<IllegalStateException> { sut.takeSnapshot(datasourceId) }

                    savedStatuses shouldBe listOf(SnapshotStatus.RUNNING, SnapshotStatus.COMPLETED, SnapshotStatus.COMPLETED)
                }
            }

            context("복호화에 실패한 경우") {
                it("RUNNING을 남기지 않고 FAILED로 저장한다") {
                    val savedStatuses = mutableListOf<SnapshotStatus>()
                    every { loadDatasourcePort.findById(datasourceId) } returns datasource
                    every { loadSnapshotPort.findByDatasourceIdAndStatus(datasourceId, SnapshotStatus.RUNNING) } returns null
                    every { dialectRegistry.getDialect(DatabaseType.POSTGRESQL) } returns mockk()
                    every { encryptionPort.decrypt(any()) } throws IllegalStateException("invalid credentials")
                    every { saveSnapshotPort.save(any()) } answers {
                        firstArg<SnapshotMeta>().also { savedStatuses.add(it.status) }
                    }

                    shouldThrow<IllegalStateException> { sut.takeSnapshot(datasourceId) }

                    savedStatuses shouldBe listOf(SnapshotStatus.RUNNING, SnapshotStatus.FAILED)
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

                    shouldThrow<io.clroot.snaplake.domain.exception.SnapshotNotFoundException> {
                        sut.delete(id)
                    }
                }
            }
        }

        describe("recoverAll") {
            context("RUNNING 상태의 스냅샷이 2개 있는 경우") {
                it("모두 FAILED로 전환하고 2를 반환한다") {
                    val snapshot1 = SnapshotMeta.start(DatasourceId.generate(), "ds-1", SnapshotType.DAILY, LocalDate.now())
                    val snapshot2 = SnapshotMeta.start(DatasourceId.generate(), "ds-2", SnapshotType.DAILY, LocalDate.now())
                    every { loadSnapshotPort.findAllByStatus(SnapshotStatus.RUNNING) } returns listOf(snapshot1, snapshot2)

                    val result = sut.recoverAll()

                    result shouldBe 2
                    snapshot1.status shouldBe SnapshotStatus.FAILED
                    snapshot2.status shouldBe SnapshotStatus.FAILED
                    verify(exactly = 2) { saveSnapshotPort.save(any()) }
                }
            }

            context("RUNNING 상태의 스냅샷이 없는 경우") {
                it("0을 반환한다") {
                    every { loadSnapshotPort.findAllByStatus(SnapshotStatus.RUNNING) } returns emptyList()

                    val result = sut.recoverAll()

                    result shouldBe 0
                    verify(exactly = 0) { saveSnapshotPort.save(any()) }
                }
            }
        }

        describe("recoverStale") {
            context("60분 전 RUNNING + 5분 전 RUNNING인 경우") {
                it("60분짜리만 FAILED로 전환하고 1을 반환한다") {
                    val staleSnapshot =
                        SnapshotMeta.reconstitute(
                            id = SnapshotId.generate(),
                            datasourceId = DatasourceId.generate(),
                            datasourceName = "ds-stale",
                            snapshotType = SnapshotType.DAILY,
                            snapshotDate = LocalDate.now(),
                            startedAt = Instant.now().minus(Duration.ofMinutes(60)),
                            status = SnapshotStatus.RUNNING,
                            completedAt = null,
                            errorMessage = null,
                            tables = emptyList(),
                        )
                    val recentSnapshot =
                        SnapshotMeta.reconstitute(
                            id = SnapshotId.generate(),
                            datasourceId = DatasourceId.generate(),
                            datasourceName = "ds-recent",
                            snapshotType = SnapshotType.DAILY,
                            snapshotDate = LocalDate.now(),
                            startedAt = Instant.now().minus(Duration.ofMinutes(5)),
                            status = SnapshotStatus.RUNNING,
                            completedAt = null,
                            errorMessage = null,
                            tables = emptyList(),
                        )
                    every { loadSnapshotPort.findAllByStatus(SnapshotStatus.RUNNING) } returns
                        listOf(staleSnapshot, recentSnapshot)

                    val result = sut.recoverStale()

                    result shouldBe 1
                    staleSnapshot.status shouldBe SnapshotStatus.FAILED
                    recentSnapshot.status shouldBe SnapshotStatus.RUNNING
                    verify(exactly = 1) { saveSnapshotPort.save(staleSnapshot) }
                }
            }
        }

        describe("takeSnapshot finally 블록") {
            val datasourceId = DatasourceId.generate()
            val datasource =
                Datasource.reconstitute(
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

            context("예상치 못한 Error 발생 시") {
                it("finally에서 FAILED로 전환한다") {
                    val dialect = mockk<DatabaseDialect>()
                    every { loadDatasourcePort.findById(datasourceId) } returns datasource
                    every { loadSnapshotPort.findByDatasourceIdAndStatus(datasourceId, SnapshotStatus.RUNNING) } returns null
                    every { encryptionPort.decrypt("encrypted") } returns "password"
                    every { dialectRegistry.getDialect(DatabaseType.POSTGRESQL) } returns dialect
                    every { dialect.createConnection(datasource, "password") } throws OutOfMemoryError("heap space")

                    try {
                        sut.takeSnapshot(datasourceId)
                    } catch (_: OutOfMemoryError) {
                        // expected
                    }

                    val savedSnapshots = mutableListOf<SnapshotMeta>()
                    verify { saveSnapshotPort.save(capture(savedSnapshots)) }
                    val lastSaved = savedSnapshots.last()
                    lastSaved.status shouldBe SnapshotStatus.FAILED
                    lastSaved.errorMessage shouldBe "Snapshot terminated unexpectedly"
                }
            }
        }
    })
