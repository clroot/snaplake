package com.snaplake.adapter.inbound.cli

import com.snaplake.application.port.inbound.InitializeSystemUseCase
import com.snaplake.application.port.inbound.TakeSnapshotUseCase
import com.snaplake.application.port.outbound.LoadDatasourcePort
import com.snaplake.domain.model.StorageType
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component

@Component
class SnaplakeCli(
    private val applicationContext: ConfigurableApplicationContext,
    private val takeSnapshotUseCase: TakeSnapshotUseCase,
    private val loadDatasourcePort: LoadDatasourcePort,
    private val initializeSystemUseCase: InitializeSystemUseCase,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String?) {
        val mode = args.firstOrNull() ?: return // "server" mode is default (no CLI action needed)

        when (mode) {
            "snapshot" -> runSnapshot(args.drop(1).filterNotNull())
            "setup" -> runSetup(args.drop(1).filterNotNull())
            "server" -> return // Let Spring Boot run normally
            else -> {
                if (mode.startsWith("--")) return // Spring Boot flags, ignore
                printUsage()
                applicationContext.close()
            }
        }
    }

    private fun runSnapshot(args: List<String>) {
        val datasourceFilter = args.find { it.startsWith("--datasource=") }
            ?.substringAfter("=")
            ?: "all"

        log.info("Running snapshot in CLI mode for datasource: {}", datasourceFilter)

        try {
            val datasources = if (datasourceFilter == "all") {
                loadDatasourcePort.findAllEnabled()
            } else {
                val ds = loadDatasourcePort.findAll().find { it.name == datasourceFilter }
                if (ds != null) listOf(ds) else {
                    log.error("Datasource '{}' not found", datasourceFilter)
                    applicationContext.close()
                    return
                }
            }

            if (datasources.isEmpty()) {
                log.warn("No datasources found")
                applicationContext.close()
                return
            }

            for (datasource in datasources) {
                log.info("Taking snapshot for datasource: {}", datasource.name)
                try {
                    val result = takeSnapshotUseCase.takeSnapshot(datasource.id)
                    log.info(
                        "Snapshot {} for '{}': {} tables, status={}",
                        result.id.value, datasource.name, result.tables.size, result.status,
                    )
                } catch (e: Exception) {
                    log.error("Failed to take snapshot for '{}': {}", datasource.name, e.message)
                }
            }
        } finally {
            applicationContext.close()
        }
    }

    private fun runSetup(args: List<String>) {
        val username = args.find { it.startsWith("--username=") }?.substringAfter("=")
        val password = args.find { it.startsWith("--password=") }?.substringAfter("=")
        val storagePath = args.find { it.startsWith("--storage-path=") }?.substringAfter("=")

        if (username == null || password == null) {
            log.error("Usage: snaplake setup --username=<admin> --password=<pass> [--storage-path=<path>]")
            applicationContext.close()
            return
        }

        try {
            initializeSystemUseCase.initialize(
                InitializeSystemUseCase.Command(
                    adminUsername = username,
                    adminPassword = password,
                    storageType = StorageType.LOCAL,
                    localPath = storagePath ?: "./data/snapshots",
                    s3Bucket = null,
                    s3Region = null,
                    s3Endpoint = null,
                    s3AccessKey = null,
                    s3SecretKey = null,
                )
            )
            log.info("System initialized successfully. Admin user: {}", username)
        } catch (e: Exception) {
            log.error("Setup failed: {}", e.message)
        } finally {
            applicationContext.close()
        }
    }

    private fun printUsage() {
        println("""
            Usage: snaplake [command] [options]
            
            Commands:
              server                          Start the web server (default)
              snapshot [--datasource=<name>]   Take a snapshot (all or specific datasource)
              setup --username=<admin> --password=<pass> [--storage-path=<path>]
                                              Initialize the system
        """.trimIndent())
    }
}
