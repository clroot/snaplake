package com.snaplake.application.service

import com.snaplake.application.port.inbound.InitializeSystemUseCase
import com.snaplake.application.port.outbound.LoadUserPort
import com.snaplake.application.port.outbound.SaveStorageConfigPort
import com.snaplake.application.port.outbound.SaveUserPort
import com.snaplake.domain.exception.SystemAlreadyInitializedException
import com.snaplake.domain.model.StorageConfig
import com.snaplake.domain.model.StorageType
import com.snaplake.domain.model.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder

class SetupServiceTest : DescribeSpec({

    val loadUserPort = mockk<LoadUserPort>()
    val saveUserPort = mockk<SaveUserPort>()
    val saveStorageConfigPort = mockk<SaveStorageConfigPort>()
    val passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    val sut = SetupService(loadUserPort, saveUserPort, saveStorageConfigPort, passwordEncoder)

    beforeTest {
        clearAllMocks()
    }

    describe("initialize") {
        context("시스템이 초기화되지 않은 경우") {
            it("admin 사용자와 storage 설정을 생성한다") {
                every { loadUserPort.existsAny() } returns false
                every { saveUserPort.save(any()) } answers { firstArg() }
                every { saveStorageConfigPort.save(any()) } answers { firstArg() }

                sut.initialize(
                    InitializeSystemUseCase.Command(
                        adminUsername = "admin",
                        adminPassword = "password123",
                        storageType = StorageType.LOCAL,
                        localPath = "/data/snapshots",
                        s3Bucket = null,
                        s3Region = null,
                        s3Endpoint = null,
                        s3AccessKey = null,
                        s3SecretKey = null,
                    )
                )

                verify { saveUserPort.save(match<User> { it.username == "admin" }) }
                verify {
                    saveStorageConfigPort.save(match<StorageConfig> {
                        it.type == StorageType.LOCAL && it.localPath == "/data/snapshots"
                    })
                }
            }
        }

        context("이미 초기화된 경우") {
            it("SystemAlreadyInitializedException을 던진다") {
                every { loadUserPort.existsAny() } returns true

                shouldThrow<SystemAlreadyInitializedException> {
                    sut.initialize(
                        InitializeSystemUseCase.Command(
                            adminUsername = "admin",
                            adminPassword = "password123",
                            storageType = StorageType.LOCAL,
                            localPath = "/data/snapshots",
                            s3Bucket = null,
                            s3Region = null,
                            s3Endpoint = null,
                            s3AccessKey = null,
                            s3SecretKey = null,
                        )
                    )
                }
            }
        }

        context("S3 storage 설정인 경우") {
            it("S3 설정으로 StorageConfig를 생성한다") {
                every { loadUserPort.existsAny() } returns false
                every { saveUserPort.save(any()) } answers { firstArg() }
                every { saveStorageConfigPort.save(any()) } answers { firstArg() }

                sut.initialize(
                    InitializeSystemUseCase.Command(
                        adminUsername = "admin",
                        adminPassword = "password123",
                        storageType = StorageType.S3,
                        localPath = null,
                        s3Bucket = "my-bucket",
                        s3Region = "us-east-1",
                        s3Endpoint = "https://s3.example.com",
                        s3AccessKey = "access-key",
                        s3SecretKey = "secret-key",
                    )
                )

                verify {
                    saveStorageConfigPort.save(match<StorageConfig> {
                        it.type == StorageType.S3 && it.s3Bucket == "my-bucket"
                    })
                }
            }
        }
    }

    describe("getStatus") {
        context("사용자가 존재하지 않는 경우") {
            it("initialized=false를 반환한다") {
                every { loadUserPort.existsAny() } returns false

                val status = sut.getStatus()

                status.initialized shouldBe false
            }
        }

        context("사용자가 존재하는 경우") {
            it("initialized=true를 반환한다") {
                every { loadUserPort.existsAny() } returns true

                val status = sut.getStatus()

                status.initialized shouldBe true
            }
        }
    }
})
