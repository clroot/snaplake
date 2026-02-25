package com.snaplake.adapter.outbound.database

import com.snaplake.application.port.outbound.ParquetWritePort
import com.snaplake.application.port.outbound.ParquetWriteResult
import org.duckdb.DuckDBConnection
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.nio.file.Files
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Types as SqlTypes

@Component
class DuckDbParquetWriter : ParquetWritePort {

    override fun writeResultSetToParquet(rs: ResultSet): ParquetWriteResult {
        val metaData = rs.metaData
        val columnCount = metaData.columnCount

        val columns = (1..columnCount).map { i ->
            ColumnDef(
                name = metaData.getColumnLabel(i),
                sqlType = metaData.getColumnType(i),
            )
        }

        val tempFile = Files.createTempFile("snaplake-duckdb-", ".parquet")

        try {
            val duckConn = DriverManager.getConnection("jdbc:duckdb:") as DuckDBConnection
            duckConn.use { conn ->
                val createTableSql = buildCreateTableSql(columns)
                conn.createStatement().use { stmt ->
                    stmt.execute(createTableSql)
                }

                val rowCount = appendRows(conn, columns, rs, columnCount)

                conn.createStatement().use { stmt ->
                    stmt.execute(
                        "COPY snap_export TO '${tempFile.toAbsolutePath()}' (FORMAT PARQUET, COMPRESSION SNAPPY)"
                    )
                }

                val data = Files.readAllBytes(tempFile)
                return ParquetWriteResult(data = data, rowCount = rowCount)
            }
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    private fun buildCreateTableSql(columns: List<ColumnDef>): String {
        val columnDefs = columns.joinToString(", ") { col ->
            "\"${col.name.replace("\"", "\"\"")}\" ${mapSqlTypeToDuckDb(col.sqlType)}"
        }
        return "CREATE TABLE snap_export ($columnDefs)"
    }

    private fun mapSqlTypeToDuckDb(sqlType: Int): String {
        return when (sqlType) {
            SqlTypes.BOOLEAN, SqlTypes.BIT -> "BOOLEAN"
            SqlTypes.TINYINT, SqlTypes.SMALLINT, SqlTypes.INTEGER -> "INTEGER"
            SqlTypes.BIGINT -> "BIGINT"
            SqlTypes.FLOAT, SqlTypes.REAL -> "FLOAT"
            SqlTypes.DOUBLE -> "DOUBLE"
            SqlTypes.NUMERIC, SqlTypes.DECIMAL -> "VARCHAR"
            SqlTypes.BINARY, SqlTypes.VARBINARY, SqlTypes.LONGVARBINARY, SqlTypes.BLOB -> "BLOB"
            else -> "VARCHAR"
        }
    }

    private fun appendRows(
        conn: DuckDBConnection,
        columns: List<ColumnDef>,
        rs: ResultSet,
        columnCount: Int,
    ): Long {
        var rowCount = 0L

        conn.createAppender(DuckDBConnection.DEFAULT_SCHEMA, "snap_export").use { appender ->
            while (rs.next()) {
                appender.beginRow()
                for (i in 1..columnCount) {
                    appendValue(appender, rs, i, columns[i - 1].sqlType)
                }
                appender.endRow()
                rowCount++

                if (rowCount % BATCH_SIZE == 0L) {
                    appender.flush()
                }
            }
        }

        return rowCount
    }

    private fun appendValue(appender: org.duckdb.DuckDBAppender, rs: ResultSet, columnIndex: Int, sqlType: Int) {
        val obj = rs.getObject(columnIndex)
        if (obj == null) {
            appender.append(null as String?)
            return
        }

        when (sqlType) {
            SqlTypes.BOOLEAN, SqlTypes.BIT ->
                appender.append((obj as? Boolean) ?: obj.toString().toBoolean())

            SqlTypes.TINYINT, SqlTypes.SMALLINT, SqlTypes.INTEGER ->
                appender.append((obj as Number).toInt())

            SqlTypes.BIGINT ->
                appender.append((obj as Number).toLong())

            SqlTypes.FLOAT, SqlTypes.REAL ->
                appender.append((obj as Number).toFloat())

            SqlTypes.DOUBLE ->
                appender.append((obj as Number).toDouble())

            SqlTypes.NUMERIC, SqlTypes.DECIMAL ->
                appender.append((obj as? BigDecimal)?.toPlainString() ?: obj.toString())

            SqlTypes.BINARY, SqlTypes.VARBINARY, SqlTypes.LONGVARBINARY, SqlTypes.BLOB -> {
                val bytes = when (obj) {
                    is ByteArray -> obj
                    is java.sql.Blob -> obj.binaryStream.use { it.readBytes() }
                    else -> obj.toString().toByteArray()
                }
                appender.append(bytes)
            }

            else ->
                appender.append(obj.toString())
        }
    }

    private data class ColumnDef(val name: String, val sqlType: Int)

    companion object {
        private const val BATCH_SIZE = 10_000L
    }
}
