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
        return path.startsWith("/assets/") ||
            path.startsWith("/favicon") ||
            path.endsWith(".js") ||
            path.endsWith(".css") ||
            path.endsWith(".png") ||
            path.endsWith(".jpg") ||
            path.endsWith(".svg") ||
            path.endsWith(".ico") ||
            path.endsWith(".woff") ||
            path.endsWith(".woff2") ||
            path == "/health"
    }

    private fun sanitizeRequestId(header: String?): String {
        if (header == null) {
            return UUID.randomUUID().toString().replace("-", "").take(16)
        }
        val sanitized = header.take(36).replace(INVALID_ID_CHARS, "")
        if (sanitized.isEmpty()) {
            return UUID.randomUUID().toString().replace("-", "").take(16)
        }
        return sanitized
    }

    companion object {
        private val INVALID_ID_CHARS = Regex("[^a-zA-Z0-9\\-]")
    }
}
