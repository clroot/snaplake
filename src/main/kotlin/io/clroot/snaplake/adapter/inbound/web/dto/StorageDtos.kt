package io.clroot.snaplake.adapter.inbound.web.dto

import io.clroot.snaplake.domain.model.StorageConfig
import jakarta.validation.constraints.NotBlank

data class StorageSettingsResponse(
    val type: String,
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
) {
    companion object {
        fun from(config: StorageConfig): StorageSettingsResponse =
            StorageSettingsResponse(
                type = config.type.name,
                localPath = config.localPath,
                s3Bucket = config.s3Bucket,
                s3Region = config.s3Region,
                s3Endpoint = config.s3Endpoint,
                s3AccessKey = config.s3AccessKey?.let { maskSecret(it) },
                s3SecretKey = config.s3SecretKey?.let { maskSecret(it) },
                smbHost = config.smbHost,
                smbPort = config.smbPort,
                smbShare = config.smbShare,
                smbPath = config.smbPath,
                smbDomain = config.smbDomain,
                smbUsername = config.smbUsername,
                smbPassword = config.smbPassword?.let { maskSecret(it) },
            )

        private fun maskSecret(value: String): String {
            if (value.length <= 4) return "****"
            return value.take(2) + "*".repeat(value.length - 4) + value.takeLast(2)
        }
    }
}

data class UpdateStorageSettingsRequest(
    @field:NotBlank val storageType: String,
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

data class StorageTestResponse(
    val success: Boolean,
)

data class StorageCacheResponse(
    val enabled: Boolean,
    val fileCount: Long,
    val totalSizeBytes: Long,
)
