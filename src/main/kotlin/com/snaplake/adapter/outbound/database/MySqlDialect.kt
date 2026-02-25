package com.snaplake.adapter.outbound.database

import com.snaplake.application.port.outbound.ConnectionTestResult
import com.snaplake.application.port.outbound.DatabaseDialect
import com.snaplake.application.port.outbound.TableInfo
import com.snaplake.domain.model.DatabaseType
import com.snaplake.domain.model.Datasource
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.DriverManager

@Component
class MySqlDialect : DatabaseDialect {

    override val type: DatabaseType = DatabaseType.MYSQL

    override fun createConnection(datasource: Datasource, decryptedPassword: String): Connection {
        val url = "jdbc:mysql://${datasource.host}:${datasource.port}/${datasource.database}"
        return DriverManager.getConnection(url, datasource.username, decryptedPassword)
    }

    override fun listTables(connection: Connection, schema: String): List<TableInfo> {
        val sql = """
            SELECT TABLE_SCHEMA, TABLE_NAME 
            FROM information_schema.tables 
            WHERE TABLE_SCHEMA = ? 
              AND TABLE_TYPE = 'BASE TABLE'
            ORDER BY TABLE_NAME
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, schema)
            stmt.executeQuery().use { rs ->
                val tables = mutableListOf<TableInfo>()
                while (rs.next()) {
                    tables.add(
                        TableInfo(
                            schema = rs.getString("TABLE_SCHEMA"),
                            name = rs.getString("TABLE_NAME"),
                        )
                    )
                }
                tables
            }
        }
    }

    override fun listPrimaryKeys(connection: Connection, schema: String, table: String): List<String> {
        val sql = """
            SELECT COLUMN_NAME
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = ?
              AND TABLE_NAME = ?
              AND CONSTRAINT_NAME = 'PRIMARY'
            ORDER BY ORDINAL_POSITION
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, schema)
            stmt.setString(2, table)
            stmt.executeQuery().use { rs ->
                val columns = mutableListOf<String>()
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"))
                }
                columns
            }
        }
    }

    override fun testConnection(datasource: Datasource, decryptedPassword: String): ConnectionTestResult {
        return try {
            createConnection(datasource, decryptedPassword).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("SELECT 1")
                }
            }
            ConnectionTestResult(success = true, message = "Connection successful")
        } catch (e: Exception) {
            ConnectionTestResult(success = false, message = e.message ?: "Unknown error")
        }
    }
}
