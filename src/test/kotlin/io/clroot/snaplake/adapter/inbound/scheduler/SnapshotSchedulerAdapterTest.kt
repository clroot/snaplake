package io.clroot.snaplake.adapter.inbound.scheduler

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.clroot.snaplake.application.port.inbound.TakeSnapshotUseCase
import io.clroot.snaplake.application.port.outbound.LoadDatasourcePort
import io.clroot.snaplake.domain.exception.SnapshotAlreadyRunningException
import io.clroot.snaplake.domain.model.DatabaseType
import io.clroot.snaplake.domain.model.Datasource
import io.clroot.snaplake.domain.model.RetentionPolicy
import io.clroot.snaplake.domain.model.SnapshotMeta
import io.clroot.snaplake.domain.model.SnapshotType
import io.clroot.snaplake.domain.vo.DatasourceId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Instant
import java.time.LocalDate

class SnapshotSchedulerAdapterTest :
    DescribeSpec({

        val taskScheduler =
            ThreadPoolTaskScheduler().apply {
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

                val updated =
                    createDatasource(
                        id = datasource.id,
                        cronExpression = "0 0 3 * * *",
                    )
                adapter.reschedule(updated)

                adapter.listScheduled() shouldHaveSize 1
                adapter.listScheduled()[0].cronExpression shouldBe "0 0 3 * * *"
            }
        }

        describe("scheduled failure reporting") {
            it("reports a FAILED result as an error with its snapshot ID") {
                val scheduler = mockk<TaskScheduler>()
                val task = slot<Runnable>()
                every { scheduler.schedule(capture(task), any<Trigger>()) } returns mockk()
                val datasource = createDatasource(cronExpression = "0 0 2 * * *")
                val failed = SnapshotMeta.start(datasource.id, datasource.name, SnapshotType.DAILY, LocalDate.now())
                failed.fail("source unavailable")
                every { takeSnapshotUseCase.takeSnapshot(datasource.id) } returns failed
                SnapshotSchedulerAdapter(scheduler, takeSnapshotUseCase, loadDatasourcePort).register(datasource)

                val events = captureSchedulerLogs { task.captured.run() }

                val error = events.single { it.level == Level.ERROR }
                error.message.startsWith("event=scheduled_snapshot_failed ") shouldBe true
                error.argumentArray[0] shouldBe datasource.id.value
                error.argumentArray[1] shouldBe datasource.name
                error.argumentArray[2] shouldBe failed.id.value
                events.count { it.level == Level.INFO } shouldBe 1
            }

            it("reports an initial save exception with its cause and keeps the scheduled task registered") {
                val scheduler = mockk<TaskScheduler>()
                val task = slot<Runnable>()
                every { scheduler.schedule(capture(task), any<Trigger>()) } returns mockk()
                val datasource = createDatasource(cronExpression = "0 0 2 * * *")
                every { takeSnapshotUseCase.takeSnapshot(datasource.id) } throws IllegalStateException("metadata unavailable")
                val adapter = SnapshotSchedulerAdapter(scheduler, takeSnapshotUseCase, loadDatasourcePort)
                adapter.register(datasource)

                val events = captureSchedulerLogs { task.captured.run() }

                val error = events.single { it.level == Level.ERROR }
                error.message.startsWith("event=scheduled_snapshot_failed ") shouldBe true
                error.argumentArray[0] shouldBe datasource.id.value
                error.throwableProxy.className shouldBe IllegalStateException::class.java.name
                events.count { it.level == Level.INFO } shouldBe 1
                adapter.listScheduled() shouldHaveSize 1
                verify(exactly = 1) { takeSnapshotUseCase.takeSnapshot(datasource.id) }
            }

            it("reports an already running datasource as a skipped run without an error") {
                val scheduler = mockk<TaskScheduler>()
                val task = slot<Runnable>()
                every { scheduler.schedule(capture(task), any<Trigger>()) } returns mockk()
                val datasource = createDatasource(cronExpression = "0 0 2 * * *")
                every { takeSnapshotUseCase.takeSnapshot(datasource.id) } throws SnapshotAlreadyRunningException(datasource.id)
                SnapshotSchedulerAdapter(scheduler, takeSnapshotUseCase, loadDatasourcePort).register(datasource)

                val events = captureSchedulerLogs { task.captured.run() }

                events.none { it.level == Level.ERROR } shouldBe true
                val warning = events.single { it.level == Level.WARN }
                warning.message.startsWith("event=scheduled_snapshot_skipped ") shouldBe true
                warning.argumentArray[0] shouldBe datasource.id.value
            }
        }
    })

private fun captureSchedulerLogs(action: () -> Unit): List<ILoggingEvent> {
    val logger = LoggerFactory.getLogger(SnapshotSchedulerAdapter::class.java) as Logger
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(appender)
    return try {
        action()
        appender.list.toList()
    } finally {
        logger.detachAppender(appender)
        appender.stop()
    }
}

private fun createDatasource(
    id: DatasourceId = DatasourceId.generate(),
    cronExpression: String?,
): Datasource =
    Datasource.reconstitute(
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
