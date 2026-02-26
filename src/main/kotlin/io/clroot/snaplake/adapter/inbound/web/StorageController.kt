package io.clroot.snaplake.adapter.inbound.web

import io.clroot.snaplake.adapter.inbound.web.dto.StorageCacheResponse
import io.clroot.snaplake.adapter.inbound.web.dto.StorageSettingsResponse
import io.clroot.snaplake.adapter.inbound.web.dto.StorageTestResponse
import io.clroot.snaplake.adapter.inbound.web.dto.UpdateStorageSettingsRequest
import io.clroot.snaplake.application.port.inbound.GetStorageSettingsUseCase
import io.clroot.snaplake.application.port.inbound.TestStorageConnectionUseCase
import io.clroot.snaplake.application.port.inbound.UpdateStorageSettingsUseCase
import io.clroot.snaplake.config.StorageProviderConfig
import io.clroot.snaplake.domain.model.StorageType
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/storage")
class StorageController(
    private val getStorageSettingsUseCase: GetStorageSettingsUseCase,
    private val updateStorageSettingsUseCase: UpdateStorageSettingsUseCase,
    private val testStorageConnectionUseCase: TestStorageConnectionUseCase,
    private val storageProviderConfig: StorageProviderConfig,
) {
    @GetMapping
    fun getSettings(): ResponseEntity<StorageSettingsResponse> {
        val config =
            getStorageSettingsUseCase.getSettings()
                ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(StorageSettingsResponse.from(config))
    }

    @PutMapping
    fun updateSettings(
        @RequestBody @Valid request: UpdateStorageSettingsRequest,
    ): ResponseEntity<StorageSettingsResponse> {
        val storageType = StorageType.valueOf(request.storageType.uppercase())
        val config =
            updateStorageSettingsUseCase.update(
                UpdateStorageSettingsUseCase.Command(
                    storageType = storageType,
                    localPath = request.localPath,
                    s3Bucket = request.s3Bucket,
                    s3Region = request.s3Region,
                    s3Endpoint = request.s3Endpoint,
                    s3AccessKey = request.s3AccessKey,
                    s3SecretKey = request.s3SecretKey,
                ),
            )
        return ResponseEntity.ok(StorageSettingsResponse.from(config))
    }

    @PostMapping("/test")
    fun testConnection(): ResponseEntity<StorageTestResponse> {
        val success = testStorageConnectionUseCase.test()
        return ResponseEntity.ok(StorageTestResponse(success = success))
    }

    @GetMapping("/cache")
    fun getCacheStatus(): ResponseEntity<StorageCacheResponse> {
        val info = storageProviderConfig.getCacheInfo()
        return ResponseEntity.ok(
            if (info != null) {
                StorageCacheResponse(
                    enabled = true,
                    fileCount = info.fileCount,
                    totalSizeBytes = info.totalSizeBytes,
                )
            } else {
                StorageCacheResponse(enabled = false, fileCount = 0, totalSizeBytes = 0)
            },
        )
    }

    @DeleteMapping("/cache")
    fun clearCache(): ResponseEntity<Void> {
        storageProviderConfig.clearCache()
        return ResponseEntity.noContent().build()
    }
}
