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
class PostgresDialect : DatabaseDialect {

    override val type: DatabaseType = DatabaseType.POSTGRESQL

    override fun createConnection(datasource: Datasource, decryptedPassword: String): Connection {
        val url = "jdbc:postgresql://${datasource.host}:${datasource.port}/${datasource.database}"
        return DriverManager.getConnection(url, datasource.username, decryptedPassword)
    }

    override fun listTables(connection: Connection, schema: String): List<TableInfo> {
        val sql = """
            SELECT table_schema, table_name 
            FROM information_schema.tables 
            WHERE table_schema = ? 
              AND table_type = 'BASE TABLE'
            ORDER BY table_name
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, schema)
            stmt.executeQuery().use { rs ->
                val tables = mutableListOf<TableInfo>()
                while (rs.next()) {
                    tables.add(
                        TableInfo(
                            schema = rs.getString("table_schema"),
                            name = rs.getString("table_name"),
                        )
                    )
                }
                tables
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
