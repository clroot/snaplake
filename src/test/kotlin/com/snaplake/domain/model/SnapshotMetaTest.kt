package com.snaplake.domain.model

import com.snaplake.domain.vo.DatasourceId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class SnapshotMetaTest : DescribeSpec({

    describe("start") {
        it("RUNNING 상태의 스냅샷을 생성한다") {
            val snapshot = SnapshotMeta.start(
                datasourceId = DatasourceId.generate(),
                datasourceName = "production-api",
                snapshotType = SnapshotType.DAILY,
                snapshotDate = LocalDate.of(2026, 2, 25),
            )

            snapshot.status shouldBe SnapshotStatus.RUNNING
            snapshot.completedAt shouldBe null
            snapshot.errorMessage shouldBe null
        }
    }

    describe("complete") {
        it("상태를 COMPLETED로 변경한다") {
            val snapshot = SnapshotMeta.start(
                datasourceId = DatasourceId.generate(),
                datasourceName = "test",
                snapshotType = SnapshotType.DAILY,
                snapshotDate = LocalDate.now(),
            )

            snapshot.complete()

            snapshot.status shouldBe SnapshotStatus.COMPLETED
        }
    }

    describe("fail") {
        it("상태를 FAILED로 변경하고 에러 메시지를 기록한다") {
            val snapshot = SnapshotMeta.start(
                datasourceId = DatasourceId.generate(),
                datasourceName = "test",
                snapshotType = SnapshotType.DAILY,
                snapshotDate = LocalDate.now(),
            )

            snapshot.fail("Connection refused")

            snapshot.status shouldBe SnapshotStatus.FAILED
            snapshot.errorMessage shouldBe "Connection refused"
        }
    }
})
