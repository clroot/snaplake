package com.snaplake.application.port.outbound

import com.snaplake.domain.model.DatabaseType
import com.snaplake.domain.model.Datasource
import java.sql.Connection

data class TableInfo(val schema: String, val name: String)
data class ConnectionTestResult(val success: Boolean, val message: String)

interface DatabaseDialect {
    val type: DatabaseType
    fun createConnection(datasource: Datasource, decryptedPassword: String): Connection
    fun listTables(connection: Connection, schema: String): List<TableInfo>
    fun listPrimaryKeys(connection: Connection, schema: String, table: String): List<String>
    fun testConnection(datasource: Datasource, decryptedPassword: String): ConnectionTestResult
}

interface DatabaseDialectRegistry {
    fun getDialect(type: DatabaseType): DatabaseDialect
}
