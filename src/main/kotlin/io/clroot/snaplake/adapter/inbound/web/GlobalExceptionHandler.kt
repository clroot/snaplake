package io.clroot.snaplake.adapter.inbound.web

import io.clroot.snaplake.adapter.inbound.web.dto.ApiError
import io.clroot.snaplake.adapter.inbound.web.dto.ApiResponse
import io.clroot.snaplake.domain.exception.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DomainException::class)
    fun handleDomainException(e: DomainException): ResponseEntity<ApiResponse<Nothing>> {
        val status =
            when (e) {
                is SystemAlreadyInitializedException -> HttpStatus.CONFLICT
                is DatasourceNotFoundException -> HttpStatus.NOT_FOUND
                is SnapshotNotFoundException -> HttpStatus.NOT_FOUND
                is SnapshotAlreadyRunningException -> HttpStatus.CONFLICT
                is DatasourceConnectionFailedException -> HttpStatus.BAD_GATEWAY
                is StorageConnectionFailedException -> HttpStatus.BAD_GATEWAY
                is InvalidCredentialsException -> HttpStatus.UNAUTHORIZED
                is QueryExecutionFailedException -> HttpStatus.BAD_REQUEST
            }

        log.warn("Domain exception: {} - {}", e.code, e.message)
        return ResponseEntity.status(status).body(
            ApiResponse(success = false, error = ApiError(code = e.code, message = e.message)),
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Map<String, String>>> {
        val errors = e.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid value") }
        log.warn("Validation error: {}", errors)
        return ResponseEntity.badRequest().body(
            ApiResponse(
                success = false,
                data = errors,
                error = ApiError(code = "VALIDATION_ERROR", message = "입력값이 올바르지 않습니다"),
            ),
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Bad request: {}", e.message)
        return ResponseEntity.badRequest().body(
            ApiResponse(success = false, error = ApiError(code = "BAD_REQUEST", message = e.message ?: "Bad request")),
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unexpected error", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiResponse(success = false, error = ApiError(code = "INTERNAL_ERROR", message = "An unexpected error occurred")),
        )
    }
}
