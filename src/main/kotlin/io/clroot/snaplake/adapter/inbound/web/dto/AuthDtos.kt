package io.clroot.snaplake.adapter.inbound.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class LoginRequest(
    @field:NotBlank val username: String,
    @field:NotBlank @field:Size(min = 4) val password: String,
)

data class LoginResponse(
    val token: String,
    val expiresAt: Instant,
)

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:NotBlank @field:Size(min = 4) val newPassword: String,
)
