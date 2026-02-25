package com.snaplake.adapter.inbound.scheduler

import com.snaplake.application.port.inbound.TakeSnapshotUseCase
import com.snaplake.application.port.outbound.LoadDatasourcePort
import com.snaplake.application.port.outbound.ScheduledTaskInfo
import com.snaplake.application.port.outbound.SnapshotSchedulerPort
import com.snaplake.domain.model.Datasource
import com.snaplake.domain.vo.DatasourceId
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Component
class SnapshotSchedulerAdapter(
    private val taskScheduler: TaskScheduler,
    private val takeSnapshotUseCase: TakeSnapshotUseCase,
    private val loadDatasourcePort: LoadDatasourcePort,
) : SnapshotSchedulerPort {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scheduledTasks = ConcurrentHashMap<DatasourceId, ScheduledTaskEntry>()

    data class ScheduledTaskEntry(
        val future: ScheduledFuture<*>,
        val datasourceName: String,
        val cronExpression: String,
    )

    @PostConstruct
    fun initializeScheduledTasks() {
        val enabledDatasources = loadDatasourcePort.findAllEnabled()
        enabledDatasources
            .filter { it.cronExpression != null }
            .forEach { register(it) }
        log.info("Initialized {} scheduled snapshot tasks", scheduledTasks.size)
    }

    override fun register(datasource: Datasource) {
        val cron = datasource.cronExpression ?: return

        unregister(datasource.id)

        try {
            val springCron = toSpringCron(cron)
            val future = taskScheduler.schedule(
                { executeSnapshot(datasource.id, datasource.name) },
                CronTrigger(springCron),
            )

            if (future != null) {
                scheduledTasks[datasource.id] = ScheduledTaskEntry(
                    future = future,
                    datasourceName = datasource.name,
                    cronExpression = cron,
                )
                log.info("Scheduled snapshot for datasource '{}' with cron '{}'", datasource.name, cron)
            }
        } catch (e: IllegalArgumentException) {
            log.error("Invalid cron expression '{}' for datasource '{}': {}", cron, datasource.name, e.message)
        }
    }

    override fun unregister(datasourceId: DatasourceId) {
        scheduledTasks.remove(datasourceId)?.let { entry ->
            entry.future.cancel(false)
            log.info("Unregistered scheduled snapshot for datasource '{}'", entry.datasourceName)
        }
    }

    override fun reschedule(datasource: Datasource) {
        unregister(datasource.id)
        if (datasource.cronExpression != null && datasource.enabled) {
            register(datasource)
        }
    }

    override fun listScheduled(): List<ScheduledTaskInfo> {
        return scheduledTasks.map { (id, entry) ->
            ScheduledTaskInfo(
                datasourceId = id,
                datasourceName = entry.datasourceName,
                cronExpression = entry.cronExpression,
                nextExecutionTime = null,
            )
        }
    }

    private fun toSpringCron(cron: String): String {
        val fields = cron.trim().split("\\s+".toRegex())
        return if (fields.size == 5) "0 $cron" else cron
    }

    private fun executeSnapshot(datasourceId: DatasourceId, datasourceName: String) {
        try {
            log.info("Starting scheduled snapshot for datasource '{}'", datasourceName)
            takeSnapshotUseCase.takeSnapshot(datasourceId)
            log.info("Completed scheduled snapshot for datasource '{}'", datasourceName)
        } catch (e: Exception) {
            log.error("Failed scheduled snapshot for datasource '{}': {}", datasourceName, e.message)
        }
    }
}
