package com.snaplake.adapter.outbound.database

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files
import java.sql.DriverManager

class ParquetWriterTest : DescribeSpec({

    describe("writeResultSetToParquet") {
        it("SQLite ResultSet를 Parquet로 변환한다") {
            val dbPath = Files.createTempFile("parquet-test", ".db")
            try {
                DriverManager.getConnection("jdbc:sqlite:${dbPath}").use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute("CREATE TABLE test_data (id INTEGER, name TEXT, score REAL, active INTEGER)")
                        stmt.execute("INSERT INTO test_data VALUES (1, 'Alice', 95.5, 1)")
                        stmt.execute("INSERT INTO test_data VALUES (2, 'Bob', 87.3, 0)")
                        stmt.execute("INSERT INTO test_data VALUES (3, 'Charlie', 92.1, 1)")
                    }

                    val rs = conn.createStatement().executeQuery("SELECT * FROM test_data ORDER BY id")
                    val parquetBytes = ParquetWriter.writeResultSetToParquet(rs)

                    parquetBytes shouldNotBe null
                    parquetBytes.isNotEmpty() shouldBe true

                    // Verify with DuckDB
                    val tempParquet = Files.createTempFile("verify-", ".parquet")
                    Files.write(tempParquet, parquetBytes)

                    DriverManager.getConnection("jdbc:duckdb:").use { duckConn ->
                        duckConn.createStatement().use { duckStmt ->
                            val duckRs = duckStmt.executeQuery("SELECT COUNT(*) as cnt FROM '${tempParquet}'")
                            duckRs.next()
                            duckRs.getInt("cnt") shouldBe 3
                        }
                    }

                    Files.deleteIfExists(tempParquet)
                }
            } finally {
                Files.deleteIfExists(dbPath)
            }
        }

        it("빈 ResultSet도 Parquet로 변환한다") {
            val dbPath = Files.createTempFile("parquet-empty-test", ".db")
            try {
                DriverManager.getConnection("jdbc:sqlite:${dbPath}").use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute("CREATE TABLE empty_data (id INTEGER, name TEXT)")
                    }

                    val rs = conn.createStatement().executeQuery("SELECT * FROM empty_data")
                    val parquetBytes = ParquetWriter.writeResultSetToParquet(rs)

                    parquetBytes shouldNotBe null
                    parquetBytes.isNotEmpty() shouldBe true

                    // Verify with DuckDB
                    val tempParquet = Files.createTempFile("verify-empty-", ".parquet")
                    Files.write(tempParquet, parquetBytes)

                    DriverManager.getConnection("jdbc:duckdb:").use { duckConn ->
                        duckConn.createStatement().use { duckStmt ->
                            val duckRs = duckStmt.executeQuery("SELECT COUNT(*) as cnt FROM '${tempParquet}'")
                            duckRs.next()
                            duckRs.getInt("cnt") shouldBe 0
                        }
                    }

                    Files.deleteIfExists(tempParquet)
                }
            } finally {
                Files.deleteIfExists(dbPath)
            }
        }
    }
})
