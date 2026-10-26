package io.clroot.snaplake.adapter.outbound.persistence.adapter

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException

@Component
class SQLiteWriteTransaction(transactionManager: PlatformTransactionManager) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val transaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    // Only metadata work belongs here. Each failed attempt must finish rolling back
    // before a fresh persistence context is used; never retry source/storage I/O.
    fun <T : Any> write(operation: String, action: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return requireNotNull(transaction.execute { action() })
            } catch (e: Exception) {
                if (!isLockContention(e)) throw e
                if (attempt == MAX_ATTEMPTS) {
                    log.error("event=sqlite_write_retries_exhausted operation={} attempts={}", operation, attempt, e)
                    throw e
                }

                val delayMillis = INITIAL_BACKOFF_MILLIS shl (attempt - 1)
                log.warn(
                    "event=sqlite_write_retry operation={} attempt={}/{} retryInMs={}",
                    operation,
                    attempt,
                    MAX_ATTEMPTS,
                    delayMillis,
                )
                try {
                    Thread.sleep(delayMillis)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw interrupted
                }
                attempt++
            }
        }
    }

    private fun isLockContention(error: Throwable): Boolean =
        generateSequence(error) { it.cause }
            .filterIsInstance<SQLiteException>()
            .any {
                // Extended codes (including SQLITE_BUSY_SNAPSHOT) share the low byte.
                val primaryCode = it.resultCode.code and 0xff
                primaryCode == SQLiteErrorCode.SQLITE_BUSY.code || primaryCode == SQLiteErrorCode.SQLITE_LOCKED.code
            }

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val INITIAL_BACKOFF_MILLIS = 100L
    }
}
