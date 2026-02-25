package com.snaplake.application.service

import com.snaplake.application.port.inbound.UpdateStorageSettingsUseCase
import com.snaplake.application.port.outbound.LoadStorageConfigPort
import com.snaplake.application.port.outbound.SaveStorageConfigPort
import com.snaplake.config.StorageProviderConfig
import com.snaplake.domain.model.StorageConfig
import com.snaplake.domain.model.StorageType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

class StorageSettingsServiceTest : DescribeSpec({

    val loadStorageConfigPort = mockk<LoadStorageConfigPort>()
    val saveStorageConfigPort = mockk<SaveStorageConfigPort>()
    val storageProviderConfig = mockk<StorageProviderConfig>(relaxed = true)

    val sut = StorageSettingsService(loadStorageConfigPort, saveStorageConfigPort, storageProviderConfig)

    beforeTest {
        clearAllMocks()
    }

    describe("getSettings") {
        it("저장된 설정을 반환한다") {
            val config = StorageConfig.local("/data/snapshots")
            every { loadStorageConfigPort.find() } returns config

            val result = sut.getSettings()

            result shouldBe config
        }

        it("설정이 없으면 null을 반환한다") {
            every { loadStorageConfigPort.find() } returns null

            val result = sut.getSettings()

            result shouldBe null
        }
    }

    describe("update") {
        it("LOCAL 설정을 저장한다") {
            every { loadStorageConfigPort.find() } returns null
            every { saveStorageConfigPort.save(any()) } answers { firstArg() }

            val result = sut.update(
                UpdateStorageSettingsUseCase.Command(
                    storageType = StorageType.LOCAL,
                    localPath = "/data/snapshots",
                    s3Bucket = null,
                    s3Region = null,
                    s3Endpoint = null,
                    s3AccessKey = null,
                    s3SecretKey = null,
                )
            )

            result.type shouldBe StorageType.LOCAL
            result.localPath shouldBe "/data/snapshots"
            verify { storageProviderConfig.refresh() }
        }

        it("S3 설정을 저장한다") {
            every { loadStorageConfigPort.find() } returns null
            every { saveStorageConfigPort.save(any()) } answers { firstArg() }

            val result = sut.update(
                UpdateStorageSettingsUseCase.Command(
                    storageType = StorageType.S3,
                    localPath = null,
                    s3Bucket = "my-bucket",
                    s3Region = "us-east-1",
                    s3Endpoint = "https://s3.example.com",
                    s3AccessKey = "access",
                    s3SecretKey = "secret",
                )
            )

            result.type shouldBe StorageType.S3
            result.s3Bucket shouldBe "my-bucket"
            verify { storageProviderConfig.refresh() }
        }

        it("S3 업데이트 시 비밀값이 null이면 기존 값을 유지한다") {
            val existingConfig = StorageConfig.s3(
                bucket = "old-bucket",
                region = "us-east-1",
                endpoint = null,
                accessKey = "existing-access-key",
                secretKey = "existing-secret-key",
            )
            every { loadStorageConfigPort.find() } returns existingConfig
            every { saveStorageConfigPort.save(any()) } answers { firstArg() }

            val result = sut.update(
                UpdateStorageSettingsUseCase.Command(
                    storageType = StorageType.S3,
                    localPath = null,
                    s3Bucket = "new-bucket",
                    s3Region = "us-west-2",
                    s3Endpoint = null,
                    s3AccessKey = null,
                    s3SecretKey = null,
                )
            )

            result.type shouldBe StorageType.S3
            result.s3Bucket shouldBe "new-bucket"
            result.s3Region shouldBe "us-west-2"
            result.s3AccessKey shouldBe "existing-access-key"
            result.s3SecretKey shouldBe "existing-secret-key"
        }
    }

    describe("test") {
        it("연결 테스트 결과를 반환한다") {
            every { storageProviderConfig.testConnection() } returns true

            sut.test() shouldBe true
        }
    }
})
