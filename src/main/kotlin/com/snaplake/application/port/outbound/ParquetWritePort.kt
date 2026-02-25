package com.snaplake.application.port.outbound

import java.sql.ResultSet

interface ParquetWritePort {
    fun writeResultSetToParquet(rs: ResultSet): ParquetWriteResult
}

data class ParquetWriteResult(
    val data: ByteArray,
    val rowCount: Long,
)
