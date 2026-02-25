package com.snaplake.application.port.inbound

import com.snaplake.domain.model.StorageConfig
import com.snaplake.domain.model.StorageType

interface GetStorageSettingsUseCase {
    fun getSettings(): StorageConfig?
}

interface UpdateStorageSettingsUseCase {
    fun update(command: Command): StorageConfig

    data class Command(
        val storageType: StorageType,
        val localPath: String?,
        val s3Bucket: String?,
        val s3Region: String?,
        val s3Endpoint: String?,
        val s3AccessKey: String?,
        val s3SecretKey: String?,
    )
}

interface TestStorageConnectionUseCase {
    fun test(): Boolean
}
