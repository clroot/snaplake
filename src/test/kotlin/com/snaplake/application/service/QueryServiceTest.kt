package com.snaplake.application.service

import com.snaplake.application.port.inbound.ExecuteQueryUseCase
import com.snaplake.application.port.outbound.LoadSnapshotPort
import com.snaplake.application.port.outbound.LoadStorageConfigPort
import com.snaplake.application.port.outbound.QueryEngine
import com.snaplake.application.port.outbound.QueryResult
import com.snaplake.application.port.outbound.StorageProvider
import com.snaplake.domain.exception.SnapshotNotFoundException
import com.snaplake.domain.model.SnapshotMeta
import com.snaplake.domain.model.SnapshotStatus
import com.snaplake.domain.model.SnapshotType
import com.snaplake.domain.model.TableMeta
import com.snaplake.domain.vo.DatasourceId
import com.snaplake.domain.vo.SnapshotId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import java.time.LocalDate

class QueryServiceTest : DescribeSpec({

    val queryEngine = mockk<QueryEngine>()
    val loadSnapshotPort = mockk<LoadSnapshotPort>()
    val loadStorageConfigPort = mockk<LoadStorageConfigPort>()
    val storageProvider = mockk<StorageProvider>()

    val sut = QueryService(queryEngine, loadSnapshotPort, loadStorageConfigPort, storageProvider)

    val emptyQueryResult = QueryResult(columns = emptyList(), rows = emptyList(), totalRows = 0)

    beforeTest { clearAllMocks() }

    describe("executeQuery") {
        context("context가 null인 경우") {
            it("viewSetupSql 없이 쿼리를 실행한다") {
                val viewSetupSqlSlot = slot<List<String>>()
                every { loadStorageConfigPort.find() } returns null
                every {
                    queryEngine.executeQuery(
                        sql = any(),
                        storageConfig = any(),
                        limit = any(),
                        offset = any(),
                        viewSetupSql = capture(viewSetupSqlSlot),
                    )
                } returns emptyQueryResult

                val command = ExecuteQueryUseCase.Command(
                    sql = "SELECT 1",
                    context = null,
                )

                sut.executeQuery(command)

                viewSetupSqlSlot.captured shouldBe emptyList()
            }
        }

        context("context가 있는 경우") {
            it("default 스냅샷의 테이블들을 VIEW로 생성하는 SQL을 전달한다") {
                val defaultSnapshotId = SnapshotId("snap-default")
                val snapshot = createTestSnapshot(
                    id = defaultSnapshotId,
                    tables = listOf(
                        TableMeta("public", "users", 100, 1024, "snapshots/users.parquet"),
                        TableMeta("public", "orders", 50, 512, "snapshots/orders.parquet"),
                    ),
                )
                val viewSetupSqlSlot = slot<List<String>>()

                every { loadStorageConfigPort.find() } returns null
                every { loadSnapshotPort.findById(defaultSnapshotId) } returns snapshot
                every { storageProvider.getUri("snapshots/users.parquet") } returns "/data/snapshots/users.parquet"
                every { storageProvider.getUri("snapshots/orders.parquet") } returns "/data/snapshots/orders.parquet"
                every {
                    queryEngine.executeQuery(
                        sql = any(),
                        storageConfig = any(),
                        limit = any(),
                        offset = any(),
                        viewSetupSql = capture(viewSetupSqlSlot),
                    )
                } returns emptyQueryResult

                val command = ExecuteQueryUseCase.Command(
                    sql = "SELECT * FROM users",
                    context = ExecuteQueryUseCase.SnapshotContext(
                        default = defaultSnapshotId,
                    ),
                )

                sut.executeQuery(command)

                val captured = viewSetupSqlSlot.captured
                captured shouldHaveSize 2
                captured shouldContain """CREATE VIEW "users" AS SELECT * FROM '/data/snapshots/users.parquet'"""
                captured shouldContain """CREATE VIEW "orders" AS SELECT * FROM '/data/snapshots/orders.parquet'"""
            }

            it("additional 스냅샷은 SCHEMA + VIEW를 생성한다") {
                val defaultSnapshotId = SnapshotId("snap-default")
                val additionalSnapshotId = SnapshotId("snap-prev")

                val defaultSnapshot = createTestSnapshot(
                    id = defaultSnapshotId,
                    tables = listOf(
                        TableMeta("public", "users", 100, 1024, "snap1/users.parquet"),
                    ),
                )
                val additionalSnapshot = createTestSnapshot(
                    id = additionalSnapshotId,
                    tables = listOf(
                        TableMeta("public", "users", 80, 900, "snap2/users.parquet"),
                        TableMeta("public", "orders", 40, 400, "snap2/orders.parquet"),
                    ),
                )
                val viewSetupSqlSlot = slot<List<String>>()

                every { loadStorageConfigPort.find() } returns null
                every { loadSnapshotPort.findById(defaultSnapshotId) } returns defaultSnapshot
                every { loadSnapshotPort.findById(additionalSnapshotId) } returns additionalSnapshot
                every { storageProvider.getUri("snap1/users.parquet") } returns "/data/snap1/users.parquet"
                every { storageProvider.getUri("snap2/users.parquet") } returns "/data/snap2/users.parquet"
                every { storageProvider.getUri("snap2/orders.parquet") } returns "/data/snap2/orders.parquet"
                every {
                    queryEngine.executeQuery(
                        sql = any(),
                        storageConfig = any(),
                        limit = any(),
                        offset = any(),
                        viewSetupSql = capture(viewSetupSqlSlot),
                    )
                } returns emptyQueryResult

                val command = ExecuteQueryUseCase.Command(
                    sql = "SELECT * FROM users EXCEPT SELECT * FROM prev.users",
                    context = ExecuteQueryUseCase.SnapshotContext(
                        default = defaultSnapshotId,
                        additional = listOf(
                            ExecuteQueryUseCase.AliasedSnapshot(
                                snapshotId = additionalSnapshotId,
                                alias = "prev",
                            ),
                        ),
                    ),
                )

                sut.executeQuery(command)

                val captured = viewSetupSqlSlot.captured
                captured shouldHaveSize 4
                captured shouldContain """CREATE VIEW "users" AS SELECT * FROM '/data/snap1/users.parquet'"""
                captured shouldContain """CREATE SCHEMA "prev""""
                captured shouldContain """CREATE VIEW "prev"."users" AS SELECT * FROM '/data/snap2/users.parquet'"""
                captured shouldContain """CREATE VIEW "prev"."orders" AS SELECT * FROM '/data/snap2/orders.parquet'"""
            }

            it("존재하지 않는 default 스냅샷이면 SnapshotNotFoundException을 던진다") {
                val missingSnapshotId = SnapshotId("snap-missing")

                every { loadStorageConfigPort.find() } returns null
                every { loadSnapshotPort.findById(missingSnapshotId) } returns null

                val command = ExecuteQueryUseCase.Command(
                    sql = "SELECT 1",
                    context = ExecuteQueryUseCase.SnapshotContext(
                        default = missingSnapshotId,
                    ),
                )

                shouldThrow<SnapshotNotFoundException> {
                    sut.executeQuery(command)
                }
            }

            it("존재하지 않는 additional 스냅샷이면 SnapshotNotFoundException을 던진다") {
                val defaultSnapshotId = SnapshotId("snap-default")
                val missingAdditionalId = SnapshotId("snap-missing-additional")

                val defaultSnapshot = createTestSnapshot(
                    id = defaultSnapshotId,
                    tables = listOf(
                        TableMeta("public", "users", 100, 1024, "snap1/users.parquet"),
                    ),
                )

                every { loadStorageConfigPort.find() } returns null
                every { loadSnapshotPort.findById(defaultSnapshotId) } returns defaultSnapshot
                every { loadSnapshotPort.findById(missingAdditionalId) } returns null
                every { storageProvider.getUri("snap1/users.parquet") } returns "/data/snap1/users.parquet"

                val command = ExecuteQueryUseCase.Command(
                    sql = "SELECT 1",
                    context = ExecuteQueryUseCase.SnapshotContext(
                        default = defaultSnapshotId,
                        additional = listOf(
                            ExecuteQueryUseCase.AliasedSnapshot(
                                snapshotId = missingAdditionalId,
                                alias = "prev",
                            ),
                        ),
                    ),
                )

                shouldThrow<SnapshotNotFoundException> {
                    sut.executeQuery(command)
                }
            }

            it("중복된 alias가 있으면 IllegalArgumentException을 던진다") {
                val defaultSnapshotId = SnapshotId("snap-default")
                val additional1 = SnapshotId("snap-a")
                val additional2 = SnapshotId("snap-b")

                val defaultSnapshot = createTestSnapshot(
                    id = defaultSnapshotId,
                    tables = listOf(
                        TableMeta("public", "users", 100, 1024, "snap1/users.parquet"),
                    ),
                )

                every { loadStorageConfigPort.find() } returns null
                every { loadSnapshotPort.findById(defaultSnapshotId) } returns defaultSnapshot
                every { storageProvider.getUri("snap1/users.parquet") } returns "/data/snap1/users.parquet"

                val command = ExecuteQueryUseCase.Command(
                    sql = "SELECT 1",
                    context = ExecuteQueryUseCase.SnapshotContext(
                        default = defaultSnapshotId,
                        additional = listOf(
                            ExecuteQueryUseCase.AliasedSnapshot(snapshotId = additional1, alias = "prev"),
                            ExecuteQueryUseCase.AliasedSnapshot(snapshotId = additional2, alias = "prev"),
                        ),
                    ),
                )

                shouldThrow<IllegalArgumentException> {
                    sut.executeQuery(command)
                }
            }

            it("유효하지 않은 alias 형식이면 IllegalArgumentException을 던진다") {
                val defaultSnapshotId = SnapshotId("snap-default")
                val additionalId = SnapshotId("snap-a")

                val defaultSnapshot = createTestSnapshot(
                    id = defaultSnapshotId,
                    tables = listOf(
                        TableMeta("public", "users", 100, 1024, "snap1/users.parquet"),
                    ),
                )

                every { loadStorageConfigPort.find() } returns null
                every { loadSnapshotPort.findById(defaultSnapshotId) } returns defaultSnapshot
                every { storageProvider.getUri("snap1/users.parquet") } returns "/data/snap1/users.parquet"

                val command = ExecuteQueryUseCase.Command(
                    sql = "SELECT 1",
                    context = ExecuteQueryUseCase.SnapshotContext(
                        default = defaultSnapshotId,
                        additional = listOf(
                            ExecuteQueryUseCase.AliasedSnapshot(snapshotId = additionalId, alias = "Invalid-Alias!"),
                        ),
                    ),
                )

                shouldThrow<IllegalArgumentException> {
                    sut.executeQuery(command)
                }
            }

            it("예약된 alias를 사용하면 IllegalArgumentException을 던진다") {
                val defaultSnapshotId = SnapshotId("snap-default")
                val additionalId = SnapshotId("snap-a")

                val defaultSnapshot = createTestSnapshot(
                    id = defaultSnapshotId,
                    tables = listOf(
                        TableMeta("public", "users", 100, 1024, "snap1/users.parquet"),
                    ),
                )

                every { loadStorageConfigPort.find() } returns null
                every { loadSnapshotPort.findById(defaultSnapshotId) } returns defaultSnapshot
                every { storageProvider.getUri("snap1/users.parquet") } returns "/data/snap1/users.parquet"

                val command = ExecuteQueryUseCase.Command(
                    sql = "SELECT 1",
                    context = ExecuteQueryUseCase.SnapshotContext(
                        default = defaultSnapshotId,
                        additional = listOf(
                            ExecuteQueryUseCase.AliasedSnapshot(snapshotId = additionalId, alias = "main"),
                        ),
                    ),
                )

                shouldThrow<IllegalArgumentException> {
                    sut.executeQuery(command)
                }
            }
        }
    }
})

private fun createTestSnapshot(
    id: SnapshotId,
    tables: List<TableMeta>,
): SnapshotMeta = SnapshotMeta.reconstitute(
    id = id,
    datasourceId = DatasourceId("ds-1"),
    datasourceName = "test-db",
    snapshotType = SnapshotType.DAILY,
    snapshotDate = LocalDate.of(2026, 1, 1),
    startedAt = Instant.now(),
    status = SnapshotStatus.COMPLETED,
    completedAt = Instant.now(),
    errorMessage = null,
    tables = tables,
)
