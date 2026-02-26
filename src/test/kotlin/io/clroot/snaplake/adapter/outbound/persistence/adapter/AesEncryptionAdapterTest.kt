package io.clroot.snaplake.adapter.outbound.persistence.adapter

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

class AesEncryptionAdapterTest :
    DescribeSpec({

        describe("키가 설정된 경우") {
            val adapter = AesEncryptionAdapter(
                configuredKey = "my-secure-encryption-key-1234",
                activeProfiles = arrayOf(),
            )

            it("encrypt/decrypt가 정상 동작한다") {
                val plainText = "Hello, Snaplake!"

                val encrypted = adapter.encrypt(plainText)
                val decrypted = adapter.decrypt(encrypted)

                decrypted shouldBe plainText
            }

            it("같은 평문이라도 매번 다른 암호문을 생성한다") {
                val plainText = "same-plaintext"

                val encrypted1 = adapter.encrypt(plainText)
                val encrypted2 = adapter.encrypt(plainText)

                encrypted1 shouldNotBe encrypted2
            }
        }

        describe("키가 16바이트 미만인 경우") {
            it("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    AesEncryptionAdapter(
                        configuredKey = "short-key",
                        activeProfiles = arrayOf(),
                    )
                }.message shouldContain "Encryption key must be at least 16 bytes"
            }
        }

        describe("키가 미설정 + dev 프로필") {
            it("기본 키로 정상 동작한다") {
                val adapter = AesEncryptionAdapter(
                    configuredKey = "",
                    activeProfiles = arrayOf("dev"),
                )

                val plainText = "dev-mode-test"
                val encrypted = adapter.encrypt(plainText)
                val decrypted = adapter.decrypt(encrypted)

                decrypted shouldBe plainText
            }
        }

        describe("키가 미설정 + prod 프로필") {
            it("IllegalStateException을 던진다") {
                val exception = shouldThrow<IllegalStateException> {
                    AesEncryptionAdapter(
                        configuredKey = "",
                        activeProfiles = arrayOf("prod"),
                    )
                }

                exception.message shouldContain "SNAPLAKE_ENCRYPTION_KEY must be set in production"
            }
        }

        describe("키가 미설정 + 프로필 없음") {
            it("IllegalStateException을 던진다") {
                val exception = shouldThrow<IllegalStateException> {
                    AesEncryptionAdapter(
                        configuredKey = "",
                        activeProfiles = arrayOf(),
                    )
                }

                exception.message shouldContain "SNAPLAKE_ENCRYPTION_KEY must be set in production"
            }
        }
    })
