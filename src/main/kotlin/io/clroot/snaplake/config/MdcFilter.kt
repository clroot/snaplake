package io.clroot.snaplake.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = sanitizeRequestId(request.getHeader("X-Request-Id"))

        MDC.put("requestId", requestId)
        MDC.put("method", request.method)
        MDC.put("uri", request.requestURI)

        val start = System.nanoTime()
        try {
            filterChain.doFilter(request, response)
        } finally {
            val elapsed = (System.nanoTime() - start) / 1_000_000
            log.info("{} {} {} {}ms", request.method, request.requestURI, response.status, elapsed)

            MDC.clear()
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return EXCLUDED_PATHS.contains(path) ||
            STATIC_RESOURCE_PREFIXES.any { path.startsWith(it) } ||
            STATIC_RESOURCE_EXTENSIONS.any { path.endsWith(it) }
    }

    private fun sanitizeRequestId(header: String?): String {
        if (header == null) {
            return generateRequestId()
        }
        val sanitized = header.take(36).replace(INVALID_ID_CHARS, "")
        if (sanitized.isEmpty()) {
            return generateRequestId()
        }
        return sanitized
    }

    private fun generateRequestId(): String =
        UUID.randomUUID().toString().replace("-", "").take(16)

    companion object {
        private val INVALID_ID_CHARS = Regex("[^a-zA-Z0-9\\-]")
        private val STATIC_RESOURCE_PREFIXES = listOf("/assets/", "/favicon")
        private val STATIC_RESOURCE_EXTENSIONS = listOf(".js", ".css", ".png", ".jpg", ".svg", ".ico", ".woff", ".woff2")
        private val EXCLUDED_PATHS = listOf("/health")
    }
}
