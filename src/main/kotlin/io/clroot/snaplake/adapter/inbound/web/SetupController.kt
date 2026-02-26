package io.clroot.snaplake.adapter.inbound.web

import io.clroot.snaplake.adapter.inbound.web.dto.InitializeRequest
import io.clroot.snaplake.adapter.inbound.web.dto.SetupStatusResponse
import io.clroot.snaplake.adapter.inbound.web.dto.StorageTestResponse
import io.clroot.snaplake.adapter.inbound.web.dto.TestStorageRequest
import io.clroot.snaplake.adapter.outbound.storage.LocalStorageAdapter
import io.clroot.snaplake.adapter.outbound.storage.S3StorageAdapter
import io.clroot.snaplake.application.port.inbound.GetSetupStatusUseCase
import io.clroot.snaplake.application.port.inbound.InitializeSystemUseCase
import io.clroot.snaplake.domain.model.StorageType
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/setup")
class SetupController(
    private val initializeSystemUseCase: InitializeSystemUseCase,
    private val getSetupStatusUseCase: GetSetupStatusUseCase,
) {
    @GetMapping("/status")
    fun getStatus(): ResponseEntity<SetupStatusResponse> {
        val status = getSetupStatusUseCase.getStatus()
        return ResponseEntity.ok(SetupStatusResponse(initialized = status.initialized))
    }

    @PostMapping("/initialize")
    fun initialize(
        @RequestBody @Valid request: InitializeRequest,
    ): ResponseEntity<Any> {
        val storageType =
            try {
                StorageType.valueOf(request.storageType.uppercase())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid storage type: ${request.storageType}")
            }

        initializeSystemUseCase.initialize(
            InitializeSystemUseCase.Command(
                adminUsername = request.adminUsername,
                adminPassword = request.adminPassword,
                storageType = storageType,
                localPath = request.localPath,
                s3Bucket = request.s3Bucket,
                s3Region = request.s3Region,
                s3Endpoint = request.s3Endpoint,
                s3AccessKey = request.s3AccessKey,
                s3SecretKey = request.s3SecretKey,
            ),
        )
        return ResponseEntity.ok().build()
    }

    @PostMapping("/test-storage")
    fun testStorage(
        @RequestBody @Valid request: TestStorageRequest,
    ): ResponseEntity<StorageTestResponse> {
        val storageType =
            try {
                StorageType.valueOf(request.storageType.uppercase())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid storage type: ${request.storageType}")
            }

        val success =
            when (storageType) {
                StorageType.LOCAL -> {
                    val path =
                        request.localPath
                            ?: throw IllegalArgumentException("Local path is required for LOCAL storage")
                    LocalStorageAdapter(path).testConnection()
                }

                StorageType.S3 -> {
                    val bucket =
                        request.s3Bucket
                            ?: throw IllegalArgumentException("S3 bucket is required for S3 storage")
                    val region =
                        request.s3Region
                            ?: throw IllegalArgumentException("S3 region is required for S3 storage")
                    S3StorageAdapter
                        .create(
                            bucket = bucket,
                            region = region,
                            endpoint = request.s3Endpoint,
                            accessKey = request.s3AccessKey,
                            secretKey = request.s3SecretKey,
                        ).testConnection()
                }
            }

        return ResponseEntity.ok(StorageTestResponse(success = success))
    }
}
