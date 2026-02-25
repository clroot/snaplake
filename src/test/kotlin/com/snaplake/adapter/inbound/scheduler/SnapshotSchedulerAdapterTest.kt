package com.snaplake.adapter.inbound.scheduler

import com.snaplake.application.port.inbound.TakeSnapshotUseCase
import com.snaplake.application.port.outbound.LoadDatasourcePort
import com.snaplake.domain.model.DatabaseType
import com.snaplake.domain.model.Datasource
import com.snaplake.domain.model.RetentionPolicy
import com.snaplake.domain.vo.DatasourceId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Instant

class SnapshotSchedulerAdapterTest : DescribeSpec({

    val taskScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 2
        setThreadNamePrefix("test-scheduler-")
        initialize()
    }
    val takeSnapshotUseCase = mockk<TakeSnapshotUseCase>(relaxed = true)
    val loadDatasourcePort = mockk<LoadDatasourcePort>()

    beforeTest {
        clearAllMocks()
        every { loadDatasourcePort.findAllEnabled() } returns emptyList()
    }

    afterSpec {
        taskScheduler.destroy()
    }

    describe("register") {
        it("6필드 Spring cron을 가진 datasource를 스케줄러에 등록한다") {
            val adapter = SnapshotSchedulerAdapter(taskScheduler, takeSnapshotUseCase, loadDatasourcePort)
            val datasource = createDatasource(cronExpression = "0 0 2 * * *")

            adapter.register(datasource)

            adapter.listScheduled() shouldHaveSize 1
            adapter.listScheduled()[0].cronExpression shouldBe "0 0 2 * * *"
        }

        it("5필드 Unix cron도 정상적으로 등록한다") {
            val adapter = SnapshotSchedulerAdapter(taskScheduler, takeSnapshotUseCase, loadDatasourcePort)
            val datasource = createDatasource(cronExpression = "0 2 * * *")

            adapter.register(datasource)

            adapter.listScheduled() shouldHaveSize 1
            adapter.listScheduled()[0].cronExpression shouldBe "0 2 * * *"
        }

        it("cron이 null인 datasource는 등록하지 않는다") {
            val adapter = SnapshotSchedulerAdapter(taskScheduler, takeSnapshotUseCase, loadDatasourcePort)
            val datasource = createDatasource(cronExpression = null)

            adapter.register(datasource)

            adapter.listScheduled() shouldHaveSize 0
        }
    }

    describe("unregister") {
        it("등록된 태스크를 제거한다") {
            val adapter = SnapshotSchedulerAdapter(taskScheduler, takeSnapshotUseCase, loadDatasourcePort)
            val datasource = createDatasource(cronExpression = "0 0 2 * * *")

            adapter.register(datasource)
            adapter.listScheduled() shouldHaveSize 1

            adapter.unregister(datasource.id)
            adapter.listScheduled() shouldHaveSize 0
        }
    }

    describe("reschedule") {
        it("기존 태스크를 제거하고 새로 등록한다") {
            val adapter = SnapshotSchedulerAdapter(taskScheduler, takeSnapshotUseCase, loadDatasourcePort)
            val datasource = createDatasource(cronExpression = "0 0 2 * * *")
            adapter.register(datasource)

            val updated = createDatasource(
                id = datasource.id,
                cronExpression = "0 0 3 * * *",
            )
            adapter.reschedule(updated)

            adapter.listScheduled() shouldHaveSize 1
            adapter.listScheduled()[0].cronExpression shouldBe "0 0 3 * * *"
        }
    }
})

private fun createDatasource(
    id: DatasourceId = DatasourceId.generate(),
    cronExpression: String?,
): Datasource = Datasource.reconstitute(
    id = id,
    name = "test-ds",
    type = DatabaseType.POSTGRESQL,
    host = "localhost",
    port = 5432,
    database = "mydb",
    username = "user",
    encryptedPassword = "enc",
    schemas = listOf("public"),
    cronExpression = cronExpression,
    retentionPolicy = RetentionPolicy(),
    enabled = true,
    createdAt = Instant.now(),
    updatedAt = Instant.now(),
)
