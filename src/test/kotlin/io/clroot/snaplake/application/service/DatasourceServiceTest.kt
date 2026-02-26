package io.clroot.snaplake.application.service

import io.clroot.snaplake.application.port.inbound.RegisterDatasourceUseCase
import io.clroot.snaplake.application.port.inbound.UpdateDatasourceUseCase
import io.clroot.snaplake.application.port.outbound.*
import io.clroot.snaplake.domain.exception.DatasourceNotFoundException
import io.clroot.snaplake.domain.model.DatabaseType
import io.clroot.snaplake.domain.model.Datasource
import io.clroot.snaplake.domain.model.RetentionPolicy
import io.clroot.snaplake.domain.vo.DatasourceId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.time.Instant

class DatasourceServiceTest :
    DescribeSpec({

        val saveDatasourcePort = mockk<SaveDatasourcePort>()
        val loadDatasourcePort = mockk<LoadDatasourcePort>()
        val encryptionPort = mockk<EncryptionPort>()
        val dialectRegistry = mockk<DatabaseDialectRegistry>()
        val snapshotSchedulerPort = mockk<SnapshotSchedulerPort>(relaxed = true)

        val sut =
            DatasourceService(
                saveDatasourcePort,
                loadDatasourcePort,
                encryptionPort,
                dialectRegistry,
                snapshotSchedulerPort,
            )

        beforeTest {
            clearAllMocks()
        }

        describe("register") {
            it("datasource를 등록하고 스케줄러에 등록한다") {
                every { encryptionPort.encrypt("password123") } returns "encrypted-password"
                every { saveDatasourcePort.save(any()) } answers { firstArg() }

                val result =
                    sut.register(
                        RegisterDatasourceUseCase.Command(
                            name = "production-db",
                            type = DatabaseType.POSTGRESQL,
                            host = "localhost",
                            port = 5432,
                            database = "mydb",
                            username = "user",
                            password = "password123",
                            schemas = listOf("public"),
                            cronExpression = "0 0 2 * * *",
                            retentionPolicy = RetentionPolicy(dailyMaxCount = 7, monthlyMaxCount = 12),
                        ),
                    )

                result.name shouldBe "production-db"
                result.encryptedPassword shouldBe "encrypted-password"
                verify { saveDatasourcePort.save(any()) }
                verify { snapshotSchedulerPort.register(any()) }
            }
        }

        describe("update") {
            val existingId = DatasourceId.generate()
            val existing =
                Datasource.reconstitute(
                    id = existingId,
                    name = "old-name",
                    type = DatabaseType.POSTGRESQL,
                    host = "localhost",
                    port = 5432,
                    database = "mydb",
                    username = "user",
                    encryptedPassword = "old-encrypted",
                    schemas = listOf("public"),
                    cronExpression = null,
                    retentionPolicy = RetentionPolicy(),
                    enabled = true,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            context("존재하는 datasource인 경우") {
                it("정보를 업데이트하고 스케줄러를 갱신한다") {
                    every { loadDatasourcePort.findById(existingId) } returns existing
                    every { encryptionPort.encrypt("new-password") } returns "new-encrypted"
                    every { saveDatasourcePort.save(any()) } answers { firstArg() }

                    val result =
                        sut.update(
                            existingId,
                            UpdateDatasourceUseCase.Command(
                                name = "new-name",
                                type = DatabaseType.POSTGRESQL,
                                host = "localhost",
                                port = 5432,
                                database = "mydb",
                                username = "user",
                                password = "new-password",
                                schemas = listOf("public"),
                                cronExpression = null,
                                retentionPolicy = RetentionPolicy(),
                            ),
                        )

                    result.name shouldBe "new-name"
                    verify { snapshotSchedulerPort.reschedule(any()) }
                }
            }

            context("이름에 경로 문자가 포함된 경우") {
                it("IllegalArgumentException을 던진다") {
                    every { loadDatasourcePort.findById(existingId) } returns existing

                    listOf("../evil-name", "path/name", "path\\name", "name..trick").forEach { invalidName ->
                        shouldThrow<IllegalArgumentException> {
                            sut.update(
                                existingId,
                                UpdateDatasourceUseCase.Command(
                                    name = invalidName,
                                    type = DatabaseType.POSTGRESQL,
                                    host = "localhost",
                                    port = 5432,
                                    database = "mydb",
                                    username = "user",
                                    password = null,
                                    schemas = listOf("public"),
                                    cronExpression = null,
                                    retentionPolicy = RetentionPolicy(),
                                ),
                            )
                        }
                    }
                }
            }

            context("존재하지 않는 datasource인 경우") {
                it("DatasourceNotFoundException을 던진다") {
                    val nonExistentId = DatasourceId.generate()
                    every { loadDatasourcePort.findById(nonExistentId) } returns null

                    shouldThrow<DatasourceNotFoundException> {
                        sut.update(
                            nonExistentId,
                            UpdateDatasourceUseCase.Command(
                                name = "name",
                                type = DatabaseType.POSTGRESQL,
                                host = "localhost",
                                port = 5432,
                                database = "mydb",
                                username = "user",
                                password = null,
                                schemas = listOf("public"),
                                cronExpression = null,
                                retentionPolicy = RetentionPolicy(),
                            ),
                        )
                    }
                }
            }
        }

        describe("delete") {
            context("존재하는 datasource인 경우") {
                it("삭제하고 스케줄러에서 제거한다") {
                    val id = DatasourceId.generate()
                    val existing =
                        Datasource.reconstitute(
                            id = id,
                            name = "test",
                            type = DatabaseType.POSTGRESQL,
                            host = "localhost",
                            port = 5432,
                            database = "mydb",
                            username = "user",
                            encryptedPassword = "enc",
                            schemas = listOf("public"),
                            cronExpression = null,
                            retentionPolicy = RetentionPolicy(),
                            enabled = true,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now(),
                        )
                    every { loadDatasourcePort.findById(id) } returns existing
                    every { saveDatasourcePort.deleteById(id) } just Runs

                    sut.delete(id)

                    verify { snapshotSchedulerPort.unregister(id) }
                    verify { saveDatasourcePort.deleteById(id) }
                }
            }
        }

        describe("test") {
            it("연결 테스트 결과를 반환한다") {
                val id = DatasourceId.generate()
                val datasource =
                    Datasource.reconstitute(
                        id = id,
                        name = "test",
                        type = DatabaseType.POSTGRESQL,
                        host = "localhost",
                        port = 5432,
                        database = "mydb",
                        username = "user",
                        encryptedPassword = "encrypted",
                        schemas = listOf("public"),
                        cronExpression = null,
                        retentionPolicy = RetentionPolicy(),
                        enabled = true,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    )
                val dialect = mockk<DatabaseDialect>()

                every { loadDatasourcePort.findById(id) } returns datasource
                every { encryptionPort.decrypt("encrypted") } returns "plain-password"
                every { dialectRegistry.getDialect(DatabaseType.POSTGRESQL) } returns dialect
                every { dialect.testConnection(datasource, "plain-password") } returns
                    ConnectionTestResult(success = true, message = "OK")

                val result = sut.test(id)
                result.success shouldBe true
            }
        }
    })
