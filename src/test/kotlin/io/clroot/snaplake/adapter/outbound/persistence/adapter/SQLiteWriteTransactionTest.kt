package io.clroot.snaplake.adapter.outbound.persistence.adapter

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException

class SQLiteWriteTransactionTest : DescribeSpec({
    describe("SQLite lock retries") {
        listOf(SQLiteErrorCode.SQLITE_BUSY, SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT, SQLiteErrorCode.SQLITE_LOCKED_SHAREDCACHE).forEach { code ->
            it("rolls back and starts a fresh transaction for wrapped $code") {
                val manager = mockk<PlatformTransactionManager>(relaxed = true)
                val transactions = mutableListOf<SimpleTransactionStatus>()
                every { manager.getTransaction(any()) } answers {
                    SimpleTransactionStatus().also { transactions.add(it) }
                }
                var attempts = 0

                val result = SQLiteWriteTransaction(manager).write("test") {
                    if (++attempts == 1) throw IllegalStateException("wrapped", SQLiteException("locked", code))
                    "saved"
                }

                result shouldBe "saved"
                attempts shouldBe 2
                transactions.size shouldBe 2
                verify(exactly = 1) { manager.rollback(transactions[0]) }
                verify(exactly = 1) { manager.commit(transactions[1]) }
            }
        }

        it("does not retry permanent SQLite errors") {
            val manager = mockk<PlatformTransactionManager>(relaxed = true)
            val failure = IllegalStateException("constraint", SQLiteException("constraint", SQLiteErrorCode.SQLITE_CONSTRAINT))
            var attempts = 0

            shouldThrow<IllegalStateException> {
                SQLiteWriteTransaction(manager).write("test") {
                    attempts++
                    throw failure
                }
            } shouldBe failure

            attempts shouldBe 1
            verify(exactly = 1) { manager.rollback(any()) }
        }

        it("stops retrying and restores the interruption flag during backoff") {
            val manager = mockk<PlatformTransactionManager>(relaxed = true)
            var attempts = 0
            try {
                shouldThrow<InterruptedException> {
                    SQLiteWriteTransaction(manager).write("test") {
                        attempts++
                        Thread.currentThread().interrupt()
                        throw SQLiteException("busy", SQLiteErrorCode.SQLITE_BUSY)
                    }
                }

                attempts shouldBe 1
                Thread.currentThread().isInterrupted shouldBe true
            } finally {
                Thread.interrupted()
            }
        }
    }
})
