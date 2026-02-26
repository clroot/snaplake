package io.clroot.snaplake.adapter.outbound.query

import io.clroot.snaplake.adapter.outbound.database.DuckDbParquetWriter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.sql.DriverManager

class DuckDbQueryEngineTest :
    DescribeSpec({

        val engine = DuckDbQueryEngine()

        describe("executeQuery") {
            it("SELECT 쿼리를 실행한다") {
                val result = engine.executeQuery("SELECT 1 as val, 'hello' as msg", null)
                result.columns shouldHaveSize 2
                result.columns[0].name shouldBe "val"
                result.rows shouldHaveSize 1
                result.totalRows shouldBe 1
            }

            it("LIMIT 적용 시 totalRows는 전체 행 수를 반환한다") {
                val tempParquet = createTestParquet()
                try {
                    val uri = tempParquet.toAbsolutePath()
                    val viewSetupSql = listOf(
                        """CREATE VIEW "test_data" AS SELECT * FROM '$uri'""",
                    )

                    val result = engine.executeQuery(
                        sql = "SELECT * FROM test_data",
                        storageConfig = null,
                        limit = 2,
                        offset = 0,
                        viewSetupSql = viewSetupSql,
                    )
                    result.rows shouldHaveSize 2
                    result.totalRows shouldBe 3
                } finally {
                    Files.deleteIfExists(tempParquet)
                }
            }

            it("INSERT 쿼리는 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    engine.executeQuery("INSERT INTO test VALUES (1)", null)
                }
            }

            it("DELETE 쿼리는 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    engine.executeQuery("DELETE FROM test", null)
                }
            }

            it("DROP 쿼리는 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    engine.executeQuery("DROP TABLE test", null)
                }
            }
        }

        describe("describeTable") {
            it("Parquet 파일의 스키마를 반환한다") {
                val tempParquet = createTestParquet()
                try {
                    val columns = engine.describeTable(tempParquet.toAbsolutePath().toString(), null)
                    columns.isNotEmpty() shouldBe true
                    columns.any { it.name == "id" } shouldBe true
                } finally {
                    Files.deleteIfExists(tempParquet)
                }
            }
        }

        describe("previewTable") {
            it("Parquet 파일의 데이터를 미리보기한다") {
                val tempParquet = createTestParquet()
                try {
                    val result =
                        engine.previewTable(
                            uri = tempParquet.toAbsolutePath().toString(),
                            storageConfig = null,
                            limit = 10,
                        )
                    result.rows shouldHaveSize 3
                    result.columns.any { it.name == "id" } shouldBe true
                    result.totalRows shouldBe 3
                } finally {
                    Files.deleteIfExists(tempParquet)
                }
            }

            it("LIMIT 적용 시 totalRows는 전체 행 수를 반환한다") {
                val tempParquet = createTestParquet()
                try {
                    val result =
                        engine.previewTable(
                            uri = tempParquet.toAbsolutePath().toString(),
                            storageConfig = null,
                            limit = 1,
                            offset = 0,
                        )
                    result.rows shouldHaveSize 1
                    result.totalRows shouldBe 3
                } finally {
                    Files.deleteIfExists(tempParquet)
                }
            }
        }

        describe("executeQuery with viewSetupSql") {
            it("VIEW를 통해 테이블 이름으로 쿼리할 수 있다") {
                val tempParquet = createTestParquet()
                try {
                    val uri = tempParquet.toAbsolutePath()
                    val viewSetupSql =
                        listOf(
                            """CREATE VIEW "test_data" AS SELECT * FROM '$uri'""",
                        )

                    val result =
                        engine.executeQuery(
                            sql = "SELECT * FROM test_data",
                            storageConfig = null,
                            viewSetupSql = viewSetupSql,
                        )
                    result.rows shouldHaveSize 3
                    result.columns.any { it.name == "id" } shouldBe true
                } finally {
                    Files.deleteIfExists(tempParquet)
                }
            }

            it("SCHEMA를 통해 alias.table 형태로 쿼리할 수 있다") {
                val tempParquet = createTestParquet()
                try {
                    val uri = tempParquet.toAbsolutePath()
                    val viewSetupSql =
                        listOf(
                            """CREATE VIEW "test_data" AS SELECT * FROM '$uri'""",
                            """CREATE SCHEMA "s2"""",
                            """CREATE VIEW "s2"."test_data" AS SELECT * FROM '$uri'""",
                        )

                    val result =
                        engine.executeQuery(
                            sql = "SELECT a.id, b.name FROM test_data a JOIN s2.test_data b ON a.id = b.id",
                            storageConfig = null,
                            viewSetupSql = viewSetupSql,
                        )
                    result.rows shouldHaveSize 3
                } finally {
                    Files.deleteIfExists(tempParquet)
                }
            }

            it("viewSetupSql이 비어있으면 기존처럼 동작한다") {
                val result =
                    engine.executeQuery(
                        sql = "SELECT 1 as val",
                        storageConfig = null,
                        viewSetupSql = emptyList(),
                    )
                result.rows shouldHaveSize 1
            }
        }

        describe("previewTable orderBy 검증") {
            it("유효한 컬럼명은 통과한다") {
                val tempParquet = createTestParquet()
                try {
                    val result =
                        engine.previewTable(
                            uri = tempParquet.toAbsolutePath().toString(),
                            storageConfig = null,
                            orderBy = "id ASC",
                            limit = 10,
                        )
                    result.rows shouldHaveSize 3
                    result.rows[0][0] shouldBe 1
                } finally {
                    Files.deleteIfExists(tempParquet)
                }
            }

            it("다중 정렬도 통과한다") {
                val tempParquet = createTestParquet()
                try {
                    val result =
                        engine.previewTable(
                            uri = tempParquet.toAbsolutePath().toString(),
                            storageConfig = null,
                            orderBy = "id DESC, name ASC",
                            limit = 10,
                        )
                    result.rows shouldHaveSize 3
                    result.rows[0][0] shouldBe 3
                } finally {
                    Files.deleteIfExists(tempParquet)
                }
            }

            it("SQL injection 시도를 거부한다") {
                val tempParquet = createTestParquet()
                try {
                    shouldThrow<IllegalArgumentException> {
                        engine.previewTable(
                            uri = tempParquet.toAbsolutePath().toString(),
                            storageConfig = null,
                            orderBy = "id; DROP TABLE users--",
                            limit = 10,
                        )
                    }
                } finally {
                    Files.deleteIfExists(tempParquet)
                }
            }

            it("서브쿼리 시도를 거부한다") {
                val tempParquet = createTestParquet()
                try {
                    shouldThrow<IllegalArgumentException> {
                        engine.previewTable(
                            uri = tempParquet.toAbsolutePath().toString(),
                            storageConfig = null,
                            orderBy = "(SELECT 1)",
                            limit = 10,
                        )
                    }
                } finally {
                    Files.deleteIfExists(tempParquet)
                }
            }
        }

        describe("countRows") {
            it("Parquet 파일의 행 수를 반환한다") {
                val tempParquet = createTestParquet()
                try {
                    val count = engine.countRows(tempParquet.toAbsolutePath().toString(), null)
                    count shouldBe 3
                } finally {
                    Files.deleteIfExists(tempParquet)
                }
            }
        }
    })

private fun createTestParquet(): java.nio.file.Path {
    val tempDb = Files.createTempFile("test-", ".db")
    val tempParquet = Files.createTempFile("test-", ".parquet")

    DriverManager.getConnection("jdbc:sqlite:$tempDb").use { conn ->
        conn.createStatement().use { stmt ->
            stmt.execute("CREATE TABLE test_data (id INTEGER, name TEXT, score REAL)")
            stmt.execute("INSERT INTO test_data VALUES (1, 'Alice', 95.5)")
            stmt.execute("INSERT INTO test_data VALUES (2, 'Bob', 87.3)")
            stmt.execute("INSERT INTO test_data VALUES (3, 'Charlie', 92.1)")
        }
        conn.createStatement().use { queryStmt ->
            val rs = queryStmt.executeQuery("SELECT * FROM test_data ORDER BY id")
            val result = DuckDbParquetWriter().writeResultSetToParquet(rs)
            Files.write(tempParquet, result.data)
        }
    }
    Files.deleteIfExists(tempDb)

    return tempParquet
}
