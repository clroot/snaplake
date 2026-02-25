package com.snaplake.application.port.outbound

interface StorageProvider {
    fun write(path: String, data: ByteArray)
    fun read(path: String): ByteArray
    fun list(prefix: String): List<String>
    fun delete(path: String)
    fun deleteAll(prefix: String)
    fun exists(path: String): Boolean
    fun getUri(path: String): String
    fun testConnection(): Boolean
}
