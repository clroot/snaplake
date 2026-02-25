package com.snaplake.application.port.inbound

import java.time.Instant

interface LoginUseCase {
    fun login(command: Command): TokenResult

    data class Command(val username: String, val password: String)
    data class TokenResult(val token: String, val expiresAt: Instant)
}

interface ChangePasswordUseCase {
    fun changePassword(command: Command)

    data class Command(val username: String, val currentPassword: String, val newPassword: String)
}
