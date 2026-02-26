package io.clroot.snaplake.config

import io.clroot.snaplake.domain.exception.DomainException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Aspect
@Component
class LoggingAspect {
    @Around("within(io.clroot.snaplake.application.service.*)")
    fun logServiceMethod(joinPoint: ProceedingJoinPoint): Any? {
        val log = LoggerFactory.getLogger(joinPoint.target.javaClass)
        val methodName = joinPoint.signature.name
        val isQuery = methodName.startsWith("get") ||
            methodName.startsWith("find") ||
            methodName.startsWith("list") ||
            methodName.startsWith("describe")

        if (isQuery) {
            log.debug("Starting: {}", methodName)
        } else {
            log.info("Starting: {}", methodName)
        }

        val start = System.nanoTime()
        return try {
            val result = joinPoint.proceed()
            val elapsed = (System.nanoTime() - start) / 1_000_000

            if (isQuery) {
                log.debug("Completed: {} ({}ms)", methodName, elapsed)
            } else {
                log.info("Completed: {} ({}ms)", methodName, elapsed)
            }

            result
        } catch (e: DomainException) {
            val elapsed = (System.nanoTime() - start) / 1_000_000
            if (isQuery) {
                log.debug("Failed: {} ({}ms) - {}", methodName, elapsed, e.message)
            } else {
                log.info("Failed: {} ({}ms) - {}", methodName, elapsed, e.message)
            }
            throw e
        } catch (e: Throwable) {
            val elapsed = (System.nanoTime() - start) / 1_000_000
            log.error("Failed: {} ({}ms) - {}", methodName, elapsed, e.message, e)
            throw e
        }
    }

    @Around("within(io.clroot.snaplake.adapter.outbound..*)")
    fun logAdapterMethod(joinPoint: ProceedingJoinPoint): Any? {
        val log = LoggerFactory.getLogger(joinPoint.target.javaClass)
        val methodName = joinPoint.signature.name

        log.debug(">>> {}", methodName)

        val start = System.nanoTime()
        return try {
            val result = joinPoint.proceed()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            log.debug("<<< {} ({}ms)", methodName, elapsed)
            result
        } catch (e: Throwable) {
            val elapsed = (System.nanoTime() - start) / 1_000_000
            log.warn("!!! {} failed ({}ms): {}", methodName, elapsed, e.message, e)
            throw e
        }
    }
}
