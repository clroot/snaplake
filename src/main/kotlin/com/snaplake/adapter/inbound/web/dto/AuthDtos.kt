package com.snaplake.adapter.inbound.web.dto

import java.time.Instant

data class LoginRequest(
    val username: String,
    val password: String,
)

data class LoginResponse(
    val token: String,
    val expiresAt: Instant,
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)
