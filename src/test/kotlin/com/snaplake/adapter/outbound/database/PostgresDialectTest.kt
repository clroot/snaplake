package com.snaplake.adapter.outbound.database

import com.snaplake.domain.model.DatabaseType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PostgresDialectTest : DescribeSpec({

    val dialect = PostgresDialect()

    describe("type") {
        it("POSTGRESQL 타입을 반환한다") {
            dialect.type shouldBe DatabaseType.POSTGRESQL
        }
    }

    describe("testConnection") {
        it("연결 실패 시 success=false를 반환한다") {
            val datasource = com.snaplake.domain.model.Datasource.reconstitute(
                id = com.snaplake.domain.vo.DatasourceId.generate(),
                name = "test-pg",
                type = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 65432,  // unlikely to have PG running here
                database = "nonexistent",
                username = "test",
                encryptedPassword = "encrypted",
                schemas = listOf("public"),
                cronExpression = null,
                retentionPolicy = com.snaplake.domain.model.RetentionPolicy(),
                enabled = true,
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
            )

            val result = dialect.testConnection(datasource, "test-password")
            result.success shouldBe false
        }
    }
})
