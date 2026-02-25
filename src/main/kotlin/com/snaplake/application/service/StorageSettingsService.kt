package com.snaplake.application.service

import com.snaplake.application.port.inbound.GetStorageSettingsUseCase
import com.snaplake.application.port.inbound.TestStorageConnectionUseCase
import com.snaplake.application.port.inbound.UpdateStorageSettingsUseCase
import com.snaplake.application.port.outbound.LoadStorageConfigPort
import com.snaplake.application.port.outbound.SaveStorageConfigPort
import com.snaplake.config.StorageProviderConfig
import com.snaplake.domain.model.StorageConfig
import com.snaplake.domain.model.StorageType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class StorageSettingsService(
    private val loadStorageConfigPort: LoadStorageConfigPort,
    private val saveStorageConfigPort: SaveStorageConfigPort,
    private val storageProviderConfig: StorageProviderConfig,
) : GetStorageSettingsUseCase, UpdateStorageSettingsUseCase, TestStorageConnectionUseCase {

    @Transactional(readOnly = true)
    override fun getSettings(): StorageConfig? {
        return loadStorageConfigPort.find()
    }

    override fun update(command: UpdateStorageSettingsUseCase.Command): StorageConfig {
        val existing = loadStorageConfigPort.find()

        val config = when (command.storageType) {
            StorageType.LOCAL -> StorageConfig.local(
                path = command.localPath
                    ?: throw IllegalArgumentException("Local path is required for LOCAL storage"),
            )
            StorageType.S3 -> {
                // If secrets are null/empty, keep existing values
                val accessKey = command.s3AccessKey?.takeIf { it.isNotBlank() }
                    ?: existing?.s3AccessKey
                val secretKey = command.s3SecretKey?.takeIf { it.isNotBlank() }
                    ?: existing?.s3SecretKey

                StorageConfig.s3(
                    bucket = command.s3Bucket
                        ?: throw IllegalArgumentException("S3 bucket is required for S3 storage"),
                    region = command.s3Region
                        ?: throw IllegalArgumentException("S3 region is required for S3 storage"),
                    endpoint = command.s3Endpoint,
                    accessKey = accessKey,
                    secretKey = secretKey,
                )
            }
        }
        val saved = saveStorageConfigPort.save(config)
        storageProviderConfig.refresh()
        return saved
    }

    @Transactional(readOnly = true)
    override fun test(): Boolean {
        return storageProviderConfig.testConnection()
    }
}
