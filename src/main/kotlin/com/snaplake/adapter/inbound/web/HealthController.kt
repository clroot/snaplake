package com.snaplake.adapter.inbound.web

import com.snaplake.application.port.outbound.StorageProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

@RestController
class HealthController(
    private val dataSource: DataSource,
    private val storageProvider: StorageProvider,
) {
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "UP"))
    }

    @GetMapping("/health/ready")
    fun ready(): ResponseEntity<Map<String, Any>> {
        val checks = mutableMapOf<String, Any>()

        val dbHealthy = try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("SELECT 1")
                }
            }
            true
        } catch (e: Exception) {
            false
        }
        checks["database"] = if (dbHealthy) "UP" else "DOWN"

        val storageHealthy = try {
            storageProvider.testConnection()
        } catch (e: Exception) {
            false
        }
        checks["storage"] = if (storageHealthy) "UP" else "DOWN"

        val allHealthy = dbHealthy && storageHealthy
        checks["status"] = if (allHealthy) "UP" else "DOWN"

        val status = if (allHealthy) {
            ResponseEntity.ok()
        } else {
            ResponseEntity.status(503)
        }
        return status.body(checks)
    }
}
