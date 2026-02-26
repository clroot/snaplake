package io.clroot.snaplake.application.service

import io.clroot.snaplake.application.port.inbound.ChangePasswordUseCase
import io.clroot.snaplake.application.port.inbound.LoginUseCase
import io.clroot.snaplake.application.port.outbound.LoadUserPort
import io.clroot.snaplake.application.port.outbound.SaveUserPort
import io.clroot.snaplake.config.JwtProvider
import io.clroot.snaplake.domain.exception.InvalidCredentialsException
import io.clroot.snaplake.domain.model.User
import org.slf4j.LoggerFactory
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
) : LoginUseCase,
    ChangePasswordUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    override fun login(command: LoginUseCase.Command): LoginUseCase.TokenResult {
        val user =
            loadUserPort.findByUsername(command.username)
                ?: run {
                    log.warn("Login failed for username: {}", sanitize(command.username))
                    throw InvalidCredentialsException()
                }

        if (!passwordEncoder.matches(command.password, user.passwordHash)) {
            log.warn("Login failed for username: {}", sanitize(command.username))
            throw InvalidCredentialsException()
        }

        val token = jwtProvider.generateToken(user.username)
        val expiresAt =
            jwtProvider.getExpirationInstant(token)
                ?: throw IllegalStateException("Failed to get token expiration")

        return LoginUseCase.TokenResult(token = token, expiresAt = expiresAt)
    }

    override fun changePassword(command: ChangePasswordUseCase.Command) {
        val user =
            loadUserPort.findByUsername(command.username)
                ?: run {
                    log.warn("Password change failed for user: {}", sanitize(command.username))
                    throw InvalidCredentialsException()
                }

        if (!passwordEncoder.matches(command.currentPassword, user.passwordHash)) {
            log.warn("Password change failed for user: {}", sanitize(command.username))
            throw InvalidCredentialsException()
        }

        val newHash = passwordEncoder.encode(command.newPassword)
        val updatedUser =
            User.reconstitute(
                id = user.id,
                username = user.username,
                passwordHash = newHash,
                role = user.role,
                createdAt = user.createdAt,
            )
        saveUserPort.save(updatedUser)
    }

    private fun sanitize(value: String): String =
        value.take(MAX_LOG_VALUE_LENGTH).replace(CONTROL_CHARS, "")

    companion object {
        private const val MAX_LOG_VALUE_LENGTH = 100
        private val CONTROL_CHARS = Regex("[\\x00-\\x1f\\x7f]")
    }
}
