package com.snaplake.application.port.outbound

import com.snaplake.domain.model.Datasource
import com.snaplake.domain.vo.DatasourceId
import java.time.Instant

interface SnapshotSchedulerPort {
    fun register(datasource: Datasource)
    fun unregister(datasourceId: DatasourceId)
    fun reschedule(datasource: Datasource)
    fun listScheduled(): List<ScheduledTaskInfo>
}

data class ScheduledTaskInfo(
    val datasourceId: DatasourceId,
    val datasourceName: String,
    val cronExpression: String,
    val nextExecutionTime: Instant?,
)
