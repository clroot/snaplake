package com.snaplake.application.service

import com.snaplake.application.port.inbound.ChangePasswordUseCase
import com.snaplake.application.port.inbound.LoginUseCase
import com.snaplake.application.port.outbound.LoadUserPort
import com.snaplake.application.port.outbound.SaveUserPort
import com.snaplake.config.JwtProvider
import com.snaplake.domain.exception.InvalidCredentialsException
import com.snaplake.domain.model.User
import com.snaplake.domain.vo.UserId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.mockk.*
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder

class AuthServiceTest : DescribeSpec({

    val loadUserPort = mockk<LoadUserPort>()
    val saveUserPort = mockk<SaveUserPort>()
    val jwtProvider = JwtProvider(
        secret = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm",
        expirationHours = 24,
    )
    val passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    val sut = AuthService(loadUserPort, saveUserPort, jwtProvider, passwordEncoder)

    beforeTest {
        clearAllMocks()
    }

    describe("login") {
        context("유효한 credentials인 경우") {
            it("JWT 토큰을 반환한다") {
                val hashedPassword = passwordEncoder.encode("password123")
                val user = User.reconstitute(
                    id = UserId.generate(),
                    username = "admin",
                    passwordHash = hashedPassword,
                    role = com.snaplake.domain.model.UserRole.ADMIN,
                    createdAt = java.time.Instant.now(),
                )
                every { loadUserPort.findByUsername("admin") } returns user

                val result = sut.login(LoginUseCase.Command("admin", "password123"))

                result.token.shouldNotBeBlank()
                result.expiresAt shouldNotBe null
            }
        }

        context("존재하지 않는 사용자인 경우") {
            it("InvalidCredentialsException을 던진다") {
                every { loadUserPort.findByUsername("nonexistent") } returns null

                shouldThrow<InvalidCredentialsException> {
                    sut.login(LoginUseCase.Command("nonexistent", "password"))
                }
            }
        }

        context("비밀번호가 틀린 경우") {
            it("InvalidCredentialsException을 던진다") {
                val hashedPassword = passwordEncoder.encode("correct-password")
                val user = User.reconstitute(
                    id = UserId.generate(),
                    username = "admin",
                    passwordHash = hashedPassword,
                    role = com.snaplake.domain.model.UserRole.ADMIN,
                    createdAt = java.time.Instant.now(),
                )
                every { loadUserPort.findByUsername("admin") } returns user

                shouldThrow<InvalidCredentialsException> {
                    sut.login(LoginUseCase.Command("admin", "wrong-password"))
                }
            }
        }
    }

    describe("changePassword") {
        context("현재 비밀번호가 맞는 경우") {
            it("비밀번호를 변경한다") {
                val currentHash = passwordEncoder.encode("old-password")
                val userId = UserId.generate()
                val user = User.reconstitute(
                    id = userId,
                    username = "admin",
                    passwordHash = currentHash,
                    role = com.snaplake.domain.model.UserRole.ADMIN,
                    createdAt = java.time.Instant.now(),
                )
                every { loadUserPort.findByUsername("admin") } returns user
                every { saveUserPort.save(any()) } answers { firstArg() }

                sut.changePassword(
                    ChangePasswordUseCase.Command(
                        username = "admin",
                        currentPassword = "old-password",
                        newPassword = "new-password",
                    )
                )

                verify { saveUserPort.save(match { passwordEncoder.matches("new-password", it.passwordHash) }) }
            }
        }

        context("현재 비밀번호가 틀린 경우") {
            it("InvalidCredentialsException을 던진다") {
                val currentHash = passwordEncoder.encode("correct-password")
                val user = User.reconstitute(
                    id = UserId.generate(),
                    username = "admin",
                    passwordHash = currentHash,
                    role = com.snaplake.domain.model.UserRole.ADMIN,
                    createdAt = java.time.Instant.now(),
                )
                every { loadUserPort.findByUsername("admin") } returns user

                shouldThrow<InvalidCredentialsException> {
                    sut.changePassword(
                        ChangePasswordUseCase.Command(
                            username = "admin",
                            currentPassword = "wrong-password",
                            newPassword = "new-password",
                        )
                    )
                }
            }
        }
    }
})
