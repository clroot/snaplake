package io.clroot.snaplake.adapter.outbound.persistence.mapper

import io.clroot.snaplake.adapter.outbound.persistence.entity.StorageConfigEntity
import io.clroot.snaplake.application.port.outbound.EncryptionPort
import io.clroot.snaplake.domain.model.StorageConfig
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

class StorageConfigMapperTest :
    DescribeSpec({

        val fakeEncryption =
            object : EncryptionPort {
                override fun encrypt(plainText: String): String = "encrypted($plainText)"

                override fun decrypt(cipherText: String): String =
                    cipherText.removePrefix("encrypted(").removeSuffix(")")
            }

        val mapper = StorageConfigMapper(fakeEncryption)

        describe("toEntity") {
            context("S3 타입 설정인 경우") {
                it("S3 자격증명에 ENC: 접두사가 붙는다") {
                    val config =
                        StorageConfig.s3(
                            bucket = "my-bucket",
                            region = "ap-northeast-2",
                            endpoint = "https://s3.example.com",
                            accessKey = "AKIAIOSFODNN7EXAMPLE",
                            secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                        )

                    val entity = mapper.toEntity(config)

                    entity.s3AccessKey shouldStartWith "ENC:"
                    entity.s3AccessKey shouldBe "ENC:encrypted(AKIAIOSFODNN7EXAMPLE)"
                    entity.s3SecretKey shouldStartWith "ENC:"
                    entity.s3SecretKey shouldBe "ENC:encrypted(wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY)"
                }
            }

            context("자격증명이 null인 경우") {
                it("null을 유지한다") {
                    val config =
                        StorageConfig.s3(
                            bucket = "my-bucket",
                            region = "ap-northeast-2",
                        )

                    val entity = mapper.toEntity(config)

                    entity.s3AccessKey.shouldBeNull()
                    entity.s3SecretKey.shouldBeNull()
                }
            }

            context("LOCAL 타입 설정인 경우") {
                it("S3 필드가 null이다") {
                    val config = StorageConfig.local("/data/snapshots")

                    val entity = mapper.toEntity(config)

                    entity.type shouldBe "LOCAL"
                    entity.localPath shouldBe "/data/snapshots"
                    entity.s3Bucket.shouldBeNull()
                    entity.s3Region.shouldBeNull()
                    entity.s3Endpoint.shouldBeNull()
                    entity.s3AccessKey.shouldBeNull()
                    entity.s3SecretKey.shouldBeNull()
                }
            }
        }

        describe("toDomain") {
            context("ENC: 접두사가 있는 값인 경우") {
                it("복호화하여 반환한다") {
                    val entity =
                        StorageConfigEntity(
                            id = 1,
                            type = "S3",
                            localPath = null,
                            s3Bucket = "my-bucket",
                            s3Region = "ap-northeast-2",
                            s3Endpoint = null,
                            s3AccessKey = "ENC:encrypted(AKIAIOSFODNN7EXAMPLE)",
                            s3SecretKey = "ENC:encrypted(wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY)",
                            updatedAt = "2026-01-01T00:00:00Z",
                        )

                    val config = mapper.toDomain(entity)

                    config.s3AccessKey shouldBe "AKIAIOSFODNN7EXAMPLE"
                    config.s3SecretKey shouldBe "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
                }
            }

            context("ENC: 접두사가 없는 값인 경우 (레거시)") {
                it("그대로 반환한다") {
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

                    val config = mapper.toDomain(entity)

                    config.s3AccessKey shouldBe "AKIAIOSFODNN7EXAMPLE"
                    config.s3SecretKey shouldBe "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
                }
            }
        }
    })
