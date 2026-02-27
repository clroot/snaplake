package io.clroot.snaplake.application.port.inbound

import io.clroot.snaplake.domain.model.StorageConfig
import io.clroot.snaplake.domain.model.StorageType

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
        val smbHost: String?,
        val smbPort: Int?,
        val smbShare: String?,
        val smbPath: String?,
        val smbDomain: String?,
        val smbUsername: String?,
        val smbPassword: String?,
    )
}

interface TestStorageConnectionUseCase {
    fun test(): Boolean
}
