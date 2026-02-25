package com.snaplake.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank

class JwtProviderTest : DescribeSpec({

    val jwtProvider = JwtProvider(
        secret = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm",
        expirationHours = 24,
    )

    describe("generateToken") {
        it("유효한 JWT 토큰을 생성한다") {
            val token = jwtProvider.generateToken("admin")

            token.shouldNotBeBlank()
        }
    }

    describe("validateTokenAndGetUsername") {
        it("유효한 토큰에서 username을 추출한다") {
            val token = jwtProvider.generateToken("admin")

            val username = jwtProvider.validateTokenAndGetUsername(token)

            username shouldBe "admin"
        }

        it("잘못된 토큰은 null을 반환한다") {
            val username = jwtProvider.validateTokenAndGetUsername("invalid-token")

            username shouldBe null
        }
    }

    describe("getExpirationInstant") {
        it("토큰의 만료 시간을 반환한다") {
            val token = jwtProvider.generateToken("admin")

            val expiration = jwtProvider.getExpirationInstant(token)

            expiration shouldNotBe null
        }
    }

    describe("만료된 토큰") {
        it("만료된 토큰은 null을 반환한다") {
            val expiredProvider = JwtProvider(
                secret = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm",
                expirationHours = 0,
            )
            val token = expiredProvider.generateToken("admin")

            // Token with 0 hours expiration should be expired immediately
            Thread.sleep(1100)
            val username = expiredProvider.validateTokenAndGetUsername(token)

            username shouldBe null
        }
    }
})
