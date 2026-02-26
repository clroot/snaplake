package com.snaplake.application.service

import com.snaplake.adapter.outbound.database.DuckDbParquetWriter
import com.snaplake.adapter.outbound.query.DuckDbQueryEngine
import com.snaplake.adapter.outbound.storage.LocalStorageAdapter
import com.snaplake.application.port.inbound.*
import com.snaplake.application.port.outbound.LoadSnapshotPort
import com.snaplake.application.port.outbound.LoadStorageConfigPort
import com.snaplake.domain.model.*
import com.snaplake.domain.vo.DatasourceId
import com.snaplake.domain.vo.SnapshotId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Files
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate

class CompareServiceTest : DescribeSpec({

    val duckDbQueryEngine = DuckDbQueryEngine()
    val loadSnapshotPort = mockk<LoadSnapshotPort>()
    val loadStorageConfigPort = mockk<LoadStorageConfigPort>()
    val tempDir = Files.createTempDirectory("compare-test")
    val storageProvider = LocalStorageAdapter(tempDir.toString())

    val sut = CompareService(duckDbQueryEngine, loadSnapshotPort, loadStorageConfigPort, storageProvider)

    val leftSnapshotId = SnapshotId.generate()
    val rightSnapshotId = SnapshotId.generate()

    beforeSpec {
        // Create left parquet (v1: Alice=95.5, Bob=87.3)
        createParquetFile(
            tempDir.resolve("left/public.users.parquet").toString(),
            listOf(
                Triple(1, "Alice", 95.5),
                Triple(2, "Bob", 87.3),
            )
        )

        // Create right parquet (v2: Alice=96.0, Charlie=92.1 -- Bob removed, Alice changed, Charlie added)
        createParquetFile(
            tempDir.resolve("right/public.users.parquet").toString(),
            listOf(
                Triple(1, "Alice", 96.0),
                Triple(3, "Charlie", 92.1),
            )
        )
    }

    beforeTest {
        clearAllMocks()
        every { loadStorageConfigPort.find() } returns null

        val leftSnapshot = SnapshotMeta.reconstitute(
            id = leftSnapshotId,
            datasourceId = DatasourceId.generate(),
            datasourceName = "test",
            snapshotType = SnapshotType.DAILY,
            snapshotDate = LocalDate.now().minusDays(1),
            startedAt = Instant.now(),
            status = SnapshotStatus.COMPLETED,
            completedAt = Instant.now(),
            errorMessage = null,
            tables = listOf(
                TableMeta("public", "users", 2, 100, "left/public.users.parquet", listOf("id")),
            ),
        )
        val rightSnapshot = SnapshotMeta.reconstitute(
            id = rightSnapshotId,
            datasourceId = DatasourceId.generate(),
            datasourceName = "test",
            snapshotType = SnapshotType.DAILY,
            snapshotDate = LocalDate.now(),
            startedAt = Instant.now(),
            status = SnapshotStatus.COMPLETED,
            completedAt = Instant.now(),
            errorMessage = null,
            tables = listOf(
                TableMeta("public", "users", 2, 100, "right/public.users.parquet", listOf("id")),
            ),
        )
        every { loadSnapshotPort.findById(leftSnapshotId) } returns leftSnapshot
        every { loadSnapshotPort.findById(rightSnapshotId) } returns rightSnapshot
    }

    afterSpec {
        tempDir.toFile().deleteRecursively()
    }

    describe("compareStats") {
        it("두 스냅샷의 통계를 비교한다") {
            val result = sut.compareStats(
                CompareStatsUseCase.Command(leftSnapshotId, rightSnapshotId, "users"),
            )

            result.leftRowCount shouldBe 2
            result.rightRowCount shouldBe 2
        }
    }

    describe("compareRows") {
        it("추가/삭제된 행을 비교한다") {
            val result = sut.compareRows(
                CompareRowsUseCase.Command(leftSnapshotId, rightSnapshotId, "users"),
            )

            result.added.rows.isNotEmpty() shouldBe true
            result.removed.rows.isNotEmpty() shouldBe true
        }
    }

    describe("compareDiff") {
        it("PK 기반 FULL OUTER JOIN으로 변경된 행을 비교한다") {
            val result = sut.compareDiff(
                CompareDiffUseCase.Command(
                    leftSnapshotId, rightSnapshotId, "users",
                    primaryKeys = listOf("id"),
                ),
            )

            result.rows.isNotEmpty() shouldBe true
            result.columns.any { it.name == "_diff_type" } shouldBe true
        }
    }

    describe("compareUnifiedDiff") {
        context("PK가 있는 테이블") {
            it("ADDED, REMOVED, CHANGED 행을 올바르게 반환한다") {
                val result = sut.compareUnifiedDiff(
                    CompareUnifiedDiffUseCase.Command(leftSnapshotId, rightSnapshotId, "users"),
                )

                result.primaryKeys shouldBe listOf("id")
                result.totalRows shouldBe 3
                result.summary.added shouldBe 1
                result.summary.removed shouldBe 1
                result.summary.changed shouldBe 1

                result.rows.filterIsInstance<DiffRow.Added>().size shouldBe 1
                result.rows.filterIsInstance<DiffRow.Removed>().size shouldBe 1

                val changed = result.rows.filterIsInstance<DiffRow.Changed>()
                changed.size shouldBe 1
                changed.first().changedColumns.isNotEmpty() shouldBe true
            }

            it("CHANGED 행에서 변경된 컬럼 인덱스가 정확하다") {
                val result = sut.compareUnifiedDiff(
                    CompareUnifiedDiffUseCase.Command(leftSnapshotId, rightSnapshotId, "users"),
                )

                val changed = result.rows.filterIsInstance<DiffRow.Changed>().first()
                val columnNames = result.columns.map { it.name }
                val scoreIndex = columnNames.indexOf("score")

                changed.changedColumns.contains(scoreIndex) shouldBe true

                val idIndex = columnNames.indexOf("id")
                changed.changedColumns.contains(idIndex) shouldBe false
            }

            it("페이지네이션이 적용되어도 totalRows는 전체 변경 수를 반환한다") {
                val result = sut.compareUnifiedDiff(
                    CompareUnifiedDiffUseCase.Command(
                        leftSnapshotId, rightSnapshotId, "users",
                        limit = 1, offset = 0,
                    ),
                )

                result.totalRows shouldBe 3
                result.rows.size shouldBe 1
            }
        }

        context("PK가 없는 테이블") {
            it("EXCEPT fallback으로 ADDED/REMOVED만 반환한다") {
                val noPkSnapshot = SnapshotMeta.reconstitute(
                    id = leftSnapshotId,
                    datasourceId = DatasourceId.generate(),
                    datasourceName = "test",
                    snapshotType = SnapshotType.DAILY,
                    snapshotDate = LocalDate.now().minusDays(1),
                    startedAt = Instant.now(),
                    status = SnapshotStatus.COMPLETED,
                    completedAt = Instant.now(),
                    errorMessage = null,
                    tables = listOf(
                        TableMeta("public", "users", 2, 100, "left/public.users.parquet", emptyList()),
                    ),
                )
                every { loadSnapshotPort.findById(leftSnapshotId) } returns noPkSnapshot

                val result = sut.compareUnifiedDiff(
                    CompareUnifiedDiffUseCase.Command(leftSnapshotId, rightSnapshotId, "users"),
                )

                result.primaryKeys shouldBe emptyList()
                result.summary.changed shouldBe 0
                result.rows.none { it is DiffRow.Changed } shouldBe true
            }
        }
    }
})

private fun createParquetFile(path: String, data: List<Triple<Int, String, Double>>) {
    val dir = java.io.File(path).parentFile
    dir.mkdirs()

    val tempDb = Files.createTempFile("compare-", ".db")
    try {
        DriverManager.getConnection("jdbc:sqlite:$tempDb").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE TABLE users (id INTEGER, name TEXT, score REAL)")
                data.forEach { (id, name, score) ->
                    stmt.execute("INSERT INTO users VALUES ($id, '$name', $score)")
                }
            }
            val rs = conn.createStatement().executeQuery("SELECT * FROM users ORDER BY id")
            val parquetBytes = DuckDbParquetWriter().writeResultSetToParquet(rs).data
            Files.write(java.nio.file.Path.of(path), parquetBytes)
        }
    } finally {
        Files.deleteIfExists(tempDb)
    }
}
