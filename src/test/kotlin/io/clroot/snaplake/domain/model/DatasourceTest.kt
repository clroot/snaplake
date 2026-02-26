package io.clroot.snaplake.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class DatasourceTest :
    DescribeSpec({

        describe("create") {
            context("유효한 파라미터인 경우") {
                it("Datasource를 생성한다") {
                    val ds =
                        Datasource.create(
                            name = "production-api",
                            type = DatabaseType.POSTGRESQL,
                            host = "localhost",
                            port = 5432,
                            database = "mydb",
                            username = "user",
                            encryptedPassword = "encrypted",
                            schemas = listOf("public"),
                            cronExpression = "0 2 * * *",
                        )

                    ds.name shouldBe "production-api"
                    ds.type shouldBe DatabaseType.POSTGRESQL
                    ds.enabled shouldBe true
                }
            }

            context("이름이 빈 문자열인 경우") {
                it("IllegalArgumentException을 던진다") {
                    shouldThrow<IllegalArgumentException> {
                        Datasource.create(
                            name = "",
                            type = DatabaseType.POSTGRESQL,
                            host = "localhost",
                            port = 5432,
                            database = "mydb",
                            username = "user",
                            encryptedPassword = "encrypted",
                            schemas = listOf("public"),
                            cronExpression = null,
                        )
                    }
                }
            }

            context("이름에 경로 문자가 포함된 경우") {
                listOf("../hack", "foo/bar", "a\\b", "test/../etc").forEach { invalidName ->
                    it("'$invalidName'은 IllegalArgumentException을 던진다") {
                        shouldThrow<IllegalArgumentException> {
                            Datasource.create(
                                name = invalidName,
                                type = DatabaseType.POSTGRESQL,
                                host = "localhost",
                                port = 5432,
                                database = "mydb",
                                username = "user",
                                encryptedPassword = "encrypted",
                                schemas = listOf("public"),
                                cronExpression = null,
                            )
                        }
                    }
                }
            }

            context("schemas가 비어있는 경우") {
                it("IllegalArgumentException을 던진다") {
                    shouldThrow<IllegalArgumentException> {
                        Datasource.create(
                            name = "test",
                            type = DatabaseType.POSTGRESQL,
                            host = "localhost",
                            port = 5432,
                            database = "mydb",
                            username = "user",
                            encryptedPassword = "encrypted",
                            schemas = emptyList(),
                            cronExpression = null,
                        )
                    }
                }
            }
        }
    })
