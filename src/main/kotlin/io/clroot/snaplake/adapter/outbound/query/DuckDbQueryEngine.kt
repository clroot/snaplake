package io.clroot.snaplake.adapter.outbound.query

import io.clroot.snaplake.application.port.outbound.ColumnSchema
import io.clroot.snaplake.application.port.outbound.QueryEngine
import io.clroot.snaplake.application.port.outbound.QueryResult
import io.clroot.snaplake.domain.exception.QueryExecutionFailedException
import io.clroot.snaplake.domain.model.StorageConfig
import io.clroot.snaplake.domain.model.StorageType
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException

@Component
class DuckDbQueryEngine : QueryEngine {
    /**
     * Executes [viewSetupSql] statements before the user [sql] query.
     * [viewSetupSql] must only contain internally-generated CREATE VIEW / CREATE SCHEMA statements.
     * The [sql] parameter is independently validated to be a SELECT-only query.
     */
    override fun executeQuery(
        sql: String,
        storageConfig: StorageConfig?,
        limit: Int,
        offset: Int,
        viewSetupSql: List<String>,
    ): QueryResult {
        validateQuery(sql)
        val countSql = "SELECT COUNT(*) AS cnt FROM ($sql) AS _q"
        val wrappedSql = "SELECT * FROM ($sql) AS _q LIMIT $limit OFFSET $offset"

        try {
            return createConnection(storageConfig).use { conn ->
                conn.createStatement().use { stmt ->
                    viewSetupSql.forEach { setupSql ->
                        stmt.execute(setupSql)
                    }

                    val totalRows = stmt.executeQuery(countSql).use { rs ->
                        if (rs.next()) rs.getLong("cnt") else 0L
                    }

                    stmt.executeQuery(wrappedSql).use { rs ->
                        resultSetToQueryResult(rs, totalRows)
                    }
                }
            }
        } catch (e: SQLException) {
            throw QueryExecutionFailedException(e.message ?: "Query execution failed")
        }
    }

    override fun describeTable(
        uri: String,
        storageConfig: StorageConfig?,
    ): List<ColumnSchema> =
        createConnection(storageConfig).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("DESCRIBE SELECT * FROM '$uri'").use { rs ->
                    val columns = mutableListOf<ColumnSchema>()
                    while (rs.next()) {
                        columns.add(
                            ColumnSchema(
                                name = rs.getString("column_name"),
                                type = rs.getString("column_type"),
                            ),
                        )
                    }
                    columns
                }
            }
        }

    override fun previewTable(
        uri: String,
        storageConfig: StorageConfig?,
        where: String?,
        orderBy: String?,
        limit: Int,
        offset: Int,
    ): QueryResult {
        val baseSql =
            buildString {
                append("SELECT * FROM '$uri'")
                if (!where.isNullOrBlank()) {
                    validateQuery("SELECT 1 WHERE $where") // Validate the WHERE clause
                    append(" WHERE $where")
                }
            }

        val countSql = "SELECT COUNT(*) AS cnt FROM ($baseSql) AS _q"
        val dataSql =
            buildString {
                append(baseSql)
                if (!orderBy.isNullOrBlank()) {
                    append(" ORDER BY ${sanitizeOrderBy(orderBy)}")
                }
                append(" LIMIT $limit OFFSET $offset")
            }

        return createConnection(storageConfig).use { conn ->
            conn.createStatement().use { stmt ->
                val totalRows = stmt.executeQuery(countSql).use { rs ->
                    if (rs.next()) rs.getLong("cnt") else 0L
                }

                stmt.executeQuery(dataSql).use { rs ->
                    resultSetToQueryResult(rs, totalRows)
                }
            }
        }
    }

    override fun countRows(
        uri: String,
        storageConfig: StorageConfig?,
    ): Long =
        createConnection(storageConfig).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) as cnt FROM '$uri'").use { rs ->
                    if (rs.next()) rs.getLong("cnt") else 0L
                }
            }
        }

    fun createConnection(storageConfig: StorageConfig?): Connection {
        val conn = DriverManager.getConnection("jdbc:duckdb:")

        conn.createStatement().use { stmt ->
            val tmpDir = System.getProperty("java.io.tmpdir") ?: "/tmp"
            stmt.execute("SET home_directory='$tmpDir'")
        }

        if (storageConfig?.type == StorageType.S3) {
            conn.createStatement().use { stmt ->
                stmt.execute("INSTALL httpfs; LOAD httpfs;")
                storageConfig.s3AccessKey?.let {
                    stmt.execute("SET s3_access_key_id='$it'")
                }
                storageConfig.s3SecretKey?.let {
                    stmt.execute("SET s3_secret_access_key='$it'")
                }
                storageConfig.s3Region?.let {
                    stmt.execute("SET s3_region='$it'")
                }
                storageConfig.s3Endpoint?.let { endpoint ->
                    val cleanEndpoint = endpoint.removePrefix("https://").removePrefix("http://")
                    stmt.execute("SET s3_endpoint='$cleanEndpoint'")
                    if (endpoint.startsWith("http://")) {
                        stmt.execute("SET s3_use_ssl=false")
                    }
                }
            }
        }

        return conn
    }

    private fun sanitizeOrderBy(orderBy: String): String {
        val pattern =
            Regex(
                """^\s*"([a-zA-Z_][a-zA-Z0-9_ ]*)"\s*(ASC|DESC)?\s*$|^\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*(ASC|DESC)?\s*$""",
                RegexOption.IGNORE_CASE,
            )
        return orderBy.split(",").map { part ->
            val match =
                pattern.matchEntire(part.trim())
                    ?: throw IllegalArgumentException("Invalid ORDER BY clause: ${part.trim()}")
            val col: String
            val dir: String
            if (match.groupValues[1].isNotEmpty()) {
                col = match.groupValues[1].trim().replace("\"", "\"\"")
                dir = match.groupValues[2].uppercase().ifEmpty { "ASC" }
            } else {
                col = match.groupValues[3].trim()
                dir = match.groupValues[4].uppercase().ifEmpty { "ASC" }
            }
            "\"$col\" $dir"
        }.joinToString(", ")
    }

    private fun validateQuery(sql: String) {
        val normalizedSql = sql.trim().uppercase()

        // Block multi-statement queries
        if (normalizedSql.contains(";")) {
            throw IllegalArgumentException("Multi-statement queries are not allowed")
        }

        val forbiddenKeywords =
            listOf(
                "INSERT ",
                "UPDATE ",
                "DELETE ",
                "DROP ",
                "CREATE ",
                "ALTER ",
                "TRUNCATE ",
                "GRANT ",
                "REVOKE ",
                "EXEC ",
                "EXECUTE ",
            )

        for (keyword in forbiddenKeywords) {
            if (normalizedSql.contains(keyword)) {
                throw IllegalArgumentException("Only SELECT queries are allowed. Found forbidden keyword: ${keyword.trim()}")
            }
        }

        if (!normalizedSql.startsWith("SELECT") && !normalizedSql.startsWith("WITH")) {
            throw IllegalArgumentException("Only SELECT queries are allowed")
        }
    }

    private fun resultSetToQueryResult(rs: ResultSet, totalRows: Long): QueryResult {
        val metaData = rs.metaData
        val columnCount = metaData.columnCount

        val columns =
            (1..columnCount).map { i ->
                ColumnSchema(
                    name = metaData.getColumnLabel(i),
                    type = metaData.getColumnTypeName(i),
                )
            }

        val rows = mutableListOf<List<Any?>>()
        while (rs.next()) {
            val row = (1..columnCount).map { i -> rs.getObject(i) }
            rows.add(row)
        }

        return QueryResult(
            columns = columns,
            rows = rows,
            totalRows = totalRows,
        )
    }
}
