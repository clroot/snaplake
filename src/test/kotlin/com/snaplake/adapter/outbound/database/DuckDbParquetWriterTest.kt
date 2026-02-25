package com.snaplake.adapter.outbound.database

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files
import java.sql.DriverManager

class DuckDbParquetWriterTest : DescribeSpec({

    val sut = DuckDbParquetWriter()

    describe("writeResultSetToParquet") {
        it("JDBC ResultSet를 Parquet ByteArray로 변환한다") {
            val dbPath = Files.createTempFile("duckdb-parquet-test", ".db")
            try {
                DriverManager.getConnection("jdbc:sqlite:${dbPath}").use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute("CREATE TABLE test_data (id INTEGER, name TEXT, score REAL, active INTEGER)")
                        stmt.execute("INSERT INTO test_data VALUES (1, 'Alice', 95.5, 1)")
                        stmt.execute("INSERT INTO test_data VALUES (2, 'Bob', 87.3, 0)")
                        stmt.execute("INSERT INTO test_data VALUES (3, 'Charlie', 92.1, 1)")
                    }

                    val rs = conn.createStatement().executeQuery("SELECT * FROM test_data ORDER BY id")
                    val result = sut.writeResultSetToParquet(rs)

                    result.data shouldNotBe null
                    result.data.isNotEmpty() shouldBe true
                    result.rowCount shouldBe 3

                    // Verify data is readable by DuckDB
                    val tempParquet = Files.createTempFile("verify-", ".parquet")
                    try {
                        Files.write(tempParquet, result.data)

                        DriverManager.getConnection("jdbc:duckdb:").use { duckConn ->
                            duckConn.createStatement().use { duckStmt ->
                                val duckRs = duckStmt.executeQuery(
                                    "SELECT * FROM '${tempParquet}' ORDER BY id"
                                )
                                duckRs.next() shouldBe true
                                duckRs.getInt("id") shouldBe 1
                                duckRs.getString("name") shouldBe "Alice"

                                duckRs.next() shouldBe true
                                duckRs.getInt("id") shouldBe 2
                                duckRs.getString("name") shouldBe "Bob"

                                duckRs.next() shouldBe true
                                duckRs.getInt("id") shouldBe 3
                                duckRs.getString("name") shouldBe "Charlie"

                                duckRs.next() shouldBe false
                            }
                        }
                    } finally {
                        Files.deleteIfExists(tempParquet)
                    }
                }
            } finally {
                Files.deleteIfExists(dbPath)
            }
        }

        it("빈 ResultSet도 Parquet로 변환한다") {
            val dbPath = Files.createTempFile("duckdb-parquet-empty-test", ".db")
            try {
                DriverManager.getConnection("jdbc:sqlite:${dbPath}").use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute("CREATE TABLE empty_data (id INTEGER, name TEXT)")
                    }

                    val rs = conn.createStatement().executeQuery("SELECT * FROM empty_data")
                    val result = sut.writeResultSetToParquet(rs)

                    result.data shouldNotBe null
                    result.data.isNotEmpty() shouldBe true
                    result.rowCount shouldBe 0

                    // Verify valid Parquet with DuckDB
                    val tempParquet = Files.createTempFile("verify-empty-", ".parquet")
                    try {
                        Files.write(tempParquet, result.data)

                        DriverManager.getConnection("jdbc:duckdb:").use { duckConn ->
                            duckConn.createStatement().use { duckStmt ->
                                val duckRs = duckStmt.executeQuery(
                                    "SELECT COUNT(*) as cnt FROM '${tempParquet}'"
                                )
                                duckRs.next()
                                duckRs.getInt("cnt") shouldBe 0
                            }
                        }
                    } finally {
                        Files.deleteIfExists(tempParquet)
                    }
                }
            } finally {
                Files.deleteIfExists(dbPath)
            }
        }

        it("NULL 값을 올바르게 처리한다") {
            val dbPath = Files.createTempFile("duckdb-parquet-null-test", ".db")
            try {
                DriverManager.getConnection("jdbc:sqlite:${dbPath}").use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute("CREATE TABLE null_data (seq INTEGER, id INTEGER, name TEXT, score REAL)")
                        stmt.execute("INSERT INTO null_data VALUES (1, 1, NULL, 95.5)")
                        stmt.execute("INSERT INTO null_data VALUES (2, 2, 'Bob', NULL)")
                        stmt.execute("INSERT INTO null_data VALUES (3, NULL, 'Charlie', 92.1)")
                    }

                    val rs = conn.createStatement().executeQuery("SELECT * FROM null_data ORDER BY seq")
                    val result = sut.writeResultSetToParquet(rs)

                    result.rowCount shouldBe 3

                    // Verify NULLs round-trip through DuckDB
                    val tempParquet = Files.createTempFile("verify-null-", ".parquet")
                    try {
                        Files.write(tempParquet, result.data)

                        DriverManager.getConnection("jdbc:duckdb:").use { duckConn ->
                            duckConn.createStatement().use { duckStmt ->
                                val duckRs = duckStmt.executeQuery(
                                    "SELECT * FROM '${tempParquet}' ORDER BY seq"
                                )

                                // Row 1: id=1, name=NULL, score=95.5
                                duckRs.next() shouldBe true
                                duckRs.getString("id") shouldBe "1"
                                duckRs.getObject("name") shouldBe null
                                duckRs.wasNull() shouldBe true

                                // Row 2: id=2, name='Bob', score=NULL
                                duckRs.next() shouldBe true
                                duckRs.getString("name") shouldBe "Bob"
                                duckRs.getObject("score") shouldBe null
                                duckRs.wasNull() shouldBe true

                                // Row 3: id=NULL, name='Charlie', score=92.1
                                duckRs.next() shouldBe true
                                duckRs.getObject("id") shouldBe null
                                duckRs.wasNull() shouldBe true
                                duckRs.getString("name") shouldBe "Charlie"
                            }
                        }
                    } finally {
                        Files.deleteIfExists(tempParquet)
                    }
                }
            } finally {
                Files.deleteIfExists(dbPath)
            }
        }

        it("다양한 SQL 타입을 올바르게 매핑한다") {
            val dbPath = Files.createTempFile("duckdb-parquet-types-test", ".db")
            try {
                DriverManager.getConnection("jdbc:sqlite:${dbPath}").use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute(
                            """
                            CREATE TABLE typed_data (
                                int_col INTEGER,
                                text_col TEXT,
                                real_col REAL,
                                blob_col BLOB,
                                numeric_col NUMERIC
                            )
                            """.trimIndent()
                        )
                        stmt.execute(
                            "INSERT INTO typed_data VALUES (42, 'hello', 3.14, X'DEADBEEF', 99.99)"
                        )
                    }

                    val rs = conn.createStatement().executeQuery("SELECT * FROM typed_data")
                    val result = sut.writeResultSetToParquet(rs)

                    result.rowCount shouldBe 1

                    // Verify with DuckDB
                    val tempParquet = Files.createTempFile("verify-types-", ".parquet")
                    try {
                        Files.write(tempParquet, result.data)

                        DriverManager.getConnection("jdbc:duckdb:").use { duckConn ->
                            duckConn.createStatement().use { duckStmt ->
                                val duckRs = duckStmt.executeQuery(
                                    "SELECT * FROM '${tempParquet}'"
                                )
                                duckRs.next() shouldBe true
                                duckRs.getString("int_col") shouldNotBe null
                                duckRs.getString("text_col") shouldBe "hello"
                                duckRs.getString("real_col") shouldNotBe null
                                duckRs.getString("numeric_col") shouldNotBe null
                            }
                        }
                    } finally {
                        Files.deleteIfExists(tempParquet)
                    }
                }
            } finally {
                Files.deleteIfExists(dbPath)
            }
        }
    }
})
