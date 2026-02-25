package com.snaplake.adapter.inbound.web

import com.snaplake.adapter.inbound.web.dto.InitializeRequest
import com.snaplake.adapter.inbound.web.dto.SetupStatusResponse
import com.snaplake.adapter.inbound.web.dto.StorageTestResponse
import com.snaplake.adapter.inbound.web.dto.TestStorageRequest
import com.snaplake.adapter.outbound.storage.LocalStorageAdapter
import com.snaplake.adapter.outbound.storage.S3StorageAdapter
import com.snaplake.application.port.inbound.GetSetupStatusUseCase
import com.snaplake.application.port.inbound.InitializeSystemUseCase
import com.snaplake.domain.model.StorageType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
    fun initialize(@RequestBody request: InitializeRequest): ResponseEntity<Any> {
        val storageType = try {
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
            )
        )
        return ResponseEntity.ok().build()
    }

    @PostMapping("/test-storage")
    fun testStorage(@RequestBody request: TestStorageRequest): ResponseEntity<StorageTestResponse> {
        val storageType = try {
            StorageType.valueOf(request.storageType.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid storage type: ${request.storageType}")
        }

        val success = when (storageType) {
            StorageType.LOCAL -> {
                val path = request.localPath
                    ?: throw IllegalArgumentException("Local path is required for LOCAL storage")
                LocalStorageAdapter(path).testConnection()
            }
            StorageType.S3 -> {
                val bucket = request.s3Bucket
                    ?: throw IllegalArgumentException("S3 bucket is required for S3 storage")
                val region = request.s3Region
                    ?: throw IllegalArgumentException("S3 region is required for S3 storage")
                S3StorageAdapter.create(
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
