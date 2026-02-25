package com.snaplake.application.service

import com.snaplake.application.port.inbound.ChangePasswordUseCase
import com.snaplake.application.port.inbound.LoginUseCase
import com.snaplake.application.port.outbound.LoadUserPort
import com.snaplake.application.port.outbound.SaveUserPort
import com.snaplake.config.JwtProvider
import com.snaplake.domain.exception.InvalidCredentialsException
import com.snaplake.domain.model.User
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuthService(
    private val loadUserPort: LoadUserPort,
    private val saveUserPort: SaveUserPort,
    private val jwtProvider: JwtProvider,
    private val passwordEncoder: Argon2PasswordEncoder,
) : LoginUseCase, ChangePasswordUseCase {

    @Transactional(readOnly = true)
    override fun login(command: LoginUseCase.Command): LoginUseCase.TokenResult {
        val user = loadUserPort.findByUsername(command.username)
            ?: throw InvalidCredentialsException()

        if (!passwordEncoder.matches(command.password, user.passwordHash)) {
            throw InvalidCredentialsException()
        }

        val token = jwtProvider.generateToken(user.username)
        val expiresAt = jwtProvider.getExpirationInstant(token)
            ?: throw IllegalStateException("Failed to get token expiration")

        return LoginUseCase.TokenResult(token = token, expiresAt = expiresAt)
    }

    override fun changePassword(command: ChangePasswordUseCase.Command) {
        val user = loadUserPort.findByUsername(command.username)
            ?: throw InvalidCredentialsException()

        if (!passwordEncoder.matches(command.currentPassword, user.passwordHash)) {
            throw InvalidCredentialsException()
        }

        val newHash = passwordEncoder.encode(command.newPassword)
        val updatedUser = User.reconstitute(
            id = user.id,
            username = user.username,
            passwordHash = newHash,
            role = user.role,
            createdAt = user.createdAt,
        )
        saveUserPort.save(updatedUser)
    }
}
