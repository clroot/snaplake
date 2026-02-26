package io.clroot.snaplake.adapter.outbound.persistence.migration

import io.clroot.snaplake.adapter.outbound.persistence.entity.StorageConfigEntity
import io.clroot.snaplake.adapter.outbound.persistence.repository.StorageConfigJpaRepository
import io.clroot.snaplake.application.port.outbound.EncryptionPort
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

class StorageConfigMigrationRunnerTest :
    DescribeSpec({

        val repository = mockk<StorageConfigJpaRepository>(relaxed = true)
        val encryptionPort = mockk<EncryptionPort>()
        val args = mockk<org.springframework.boot.ApplicationArguments>()

        val sut = StorageConfigMigrationRunner(repository, encryptionPort)

        beforeTest {
            clearAllMocks()
        }

        describe("run") {
            context("평문 자격증명이 있는 경우") {
                it("accessKey와 secretKey가 ENC: 접두사로 암호화된다") {
                    val entity =
                        StorageConfigEntity(
                            id = 1,
                            type = "S3",
                            localPath = null,
                            s3Bucket = "my-bucket",
                            s3Region = "ap-northeast-2",
                            s3Endpoint = null,
                            s3AccessKey = "AKIAIOSFODNN7EXAMPLE",
                            s3SecretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                            updatedAt = "2026-01-01T00:00:00Z",
                        )
                    every { repository.findById(1) } returns Optional.of(entity)
                    every { encryptionPort.encrypt("AKIAIOSFODNN7EXAMPLE") } returns "encrypted-access"
                    every { encryptionPort.encrypt("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY") } returns "encrypted-secret"
                    every { repository.save(any()) } returns entity

                    sut.run(args)

                    entity.s3AccessKey shouldBe "ENC:encrypted-access"
                    entity.s3SecretKey shouldBe "ENC:encrypted-secret"
                    verify(exactly = 1) { repository.save(entity) }
                }
            }

            context("이미 암호화된 자격증명이 있는 경우") {
                it("변경되지 않는다") {
                    val entity =
                        StorageConfigEntity(
                            id = 1,
                            type = "S3",
                            localPath = null,
                            s3Bucket = "my-bucket",
                            s3Region = "ap-northeast-2",
                            s3Endpoint = null,
                            s3AccessKey = "ENC:already-encrypted-access",
                            s3SecretKey = "ENC:already-encrypted-secret",
                            updatedAt = "2026-01-01T00:00:00Z",
                        )
                    every { repository.findById(1) } returns Optional.of(entity)

                    sut.run(args)

                    entity.s3AccessKey shouldBe "ENC:already-encrypted-access"
                    entity.s3SecretKey shouldBe "ENC:already-encrypted-secret"
                    verify(exactly = 0) { encryptionPort.encrypt(any()) }
                    verify(exactly = 0) { repository.save(any()) }
                }
            }

            context("설정이 없는 경우") {
                it("아무 작업도 하지 않는다") {
                    every { repository.findById(1) } returns Optional.empty()

                    sut.run(args)

                    verify(exactly = 0) { encryptionPort.encrypt(any()) }
                    verify(exactly = 0) { repository.save(any()) }
                }
            }

            context("자격증명이 null인 경우") {
                it("아무 변경 없다") {
                    val entity =
                        StorageConfigEntity(
                            id = 1,
                            type = "S3",
                            localPath = null,
                            s3Bucket = "my-bucket",
                            s3Region = "ap-northeast-2",
                            s3Endpoint = null,
                            s3AccessKey = null,
                            s3SecretKey = null,
                            updatedAt = "2026-01-01T00:00:00Z",
                        )
                    every { repository.findById(1) } returns Optional.of(entity)

                    sut.run(args)

                    entity.s3AccessKey shouldBe null
                    entity.s3SecretKey shouldBe null
                    verify(exactly = 0) { encryptionPort.encrypt(any()) }
                    verify(exactly = 0) { repository.save(any()) }
                }
            }
        }
    })
