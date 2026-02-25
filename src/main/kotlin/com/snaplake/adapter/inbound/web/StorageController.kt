package com.snaplake.adapter.inbound.web

import com.snaplake.adapter.inbound.web.dto.StorageSettingsResponse
import com.snaplake.adapter.inbound.web.dto.StorageTestResponse
import com.snaplake.adapter.inbound.web.dto.UpdateStorageSettingsRequest
import com.snaplake.application.port.inbound.GetStorageSettingsUseCase
import com.snaplake.application.port.inbound.TestStorageConnectionUseCase
import com.snaplake.application.port.inbound.UpdateStorageSettingsUseCase
import com.snaplake.domain.model.StorageType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/storage")
class StorageController(
    private val getStorageSettingsUseCase: GetStorageSettingsUseCase,
    private val updateStorageSettingsUseCase: UpdateStorageSettingsUseCase,
    private val testStorageConnectionUseCase: TestStorageConnectionUseCase,
) {
    @GetMapping
    fun getSettings(): ResponseEntity<StorageSettingsResponse> {
        val config = getStorageSettingsUseCase.getSettings()
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(StorageSettingsResponse.from(config))
    }

    @PutMapping
    fun updateSettings(@RequestBody request: UpdateStorageSettingsRequest): ResponseEntity<StorageSettingsResponse> {
        val storageType = StorageType.valueOf(request.storageType.uppercase())
        val config = updateStorageSettingsUseCase.update(
            UpdateStorageSettingsUseCase.Command(
                storageType = storageType,
                localPath = request.localPath,
                s3Bucket = request.s3Bucket,
                s3Region = request.s3Region,
                s3Endpoint = request.s3Endpoint,
                s3AccessKey = request.s3AccessKey,
                s3SecretKey = request.s3SecretKey,
            )
        )
        return ResponseEntity.ok(StorageSettingsResponse.from(config))
    }

    @PostMapping("/test")
    fun testConnection(): ResponseEntity<StorageTestResponse> {
        val success = testStorageConnectionUseCase.test()
        return ResponseEntity.ok(StorageTestResponse(success = success))
    }
}
