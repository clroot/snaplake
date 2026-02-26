package io.clroot.snaplake.adapter.inbound.web

import io.clroot.snaplake.adapter.inbound.web.dto.ChangePasswordRequest
import io.clroot.snaplake.adapter.inbound.web.dto.LoginRequest
import io.clroot.snaplake.adapter.inbound.web.dto.LoginResponse
import io.clroot.snaplake.application.port.inbound.ChangePasswordUseCase
import io.clroot.snaplake.application.port.inbound.LoginUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val loginUseCase: LoginUseCase,
    private val changePasswordUseCase: ChangePasswordUseCase,
) {
    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: LoginRequest,
    ): ResponseEntity<LoginResponse> {
        val result =
            loginUseCase.login(
                LoginUseCase.Command(
                    username = request.username,
                    password = request.password,
                ),
            )
        return ResponseEntity.ok(
            LoginResponse(
                token = result.token,
                expiresAt = result.expiresAt,
            ),
        )
    }

    @PostMapping("/change-password")
    fun changePassword(
        @RequestBody @Valid request: ChangePasswordRequest,
    ): ResponseEntity<Void> {
        val username =
            SecurityContextHolder.getContext().authentication?.name
                ?: return ResponseEntity.status(401).build()

        changePasswordUseCase.changePassword(
            ChangePasswordUseCase.Command(
                username = username,
                currentPassword = request.currentPassword,
                newPassword = request.newPassword,
            ),
        )
        return ResponseEntity.ok().build()
    }
}
