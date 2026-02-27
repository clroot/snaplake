package io.clroot.snaplake.application.service

import io.clroot.snaplake.application.port.inbound.GetStorageSettingsUseCase
import io.clroot.snaplake.application.port.inbound.TestStorageConnectionUseCase
import io.clroot.snaplake.application.port.inbound.UpdateStorageSettingsUseCase
import io.clroot.snaplake.application.port.outbound.LoadStorageConfigPort
import io.clroot.snaplake.application.port.outbound.SaveStorageConfigPort
import io.clroot.snaplake.config.StorageProviderConfig
import io.clroot.snaplake.domain.model.StorageConfig
import io.clroot.snaplake.domain.model.StorageType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class StorageSettingsService(
    private val loadStorageConfigPort: LoadStorageConfigPort,
    private val saveStorageConfigPort: SaveStorageConfigPort,
    private val storageProviderConfig: StorageProviderConfig,
) : GetStorageSettingsUseCase,
    UpdateStorageSettingsUseCase,
    TestStorageConnectionUseCase {
    @Transactional(readOnly = true)
    override fun getSettings(): StorageConfig? = loadStorageConfigPort.find()

    override fun update(command: UpdateStorageSettingsUseCase.Command): StorageConfig {
        val existing = loadStorageConfigPort.find()

        val config =
            when (command.storageType) {
                StorageType.LOCAL -> {
                    StorageConfig.local(
                        path =
                            command.localPath
                                ?: throw IllegalArgumentException("Local path is required for LOCAL storage"),
                    )
                }

                StorageType.S3 -> {
                    // If secrets are null/empty, keep existing values
                    val accessKey =
                        command.s3AccessKey?.takeIf { it.isNotBlank() }
                            ?: existing?.s3AccessKey
                    val secretKey =
                        command.s3SecretKey?.takeIf { it.isNotBlank() }
                            ?: existing?.s3SecretKey

                    StorageConfig.s3(
                        bucket =
                            command.s3Bucket
                                ?: throw IllegalArgumentException("S3 bucket is required for S3 storage"),
                        region =
                            command.s3Region
                                ?: throw IllegalArgumentException("S3 region is required for S3 storage"),
                        endpoint = command.s3Endpoint,
                        accessKey = accessKey,
                        secretKey = secretKey,
                    )
                }

                StorageType.SMB -> {
                    val password =
                        command.smbPassword?.takeIf { it.isNotBlank() }
                            ?: existing?.smbPassword

                    StorageConfig.smb(
                        host =
                            command.smbHost
                                ?: throw IllegalArgumentException("SMB host is required for SMB storage"),
                        share =
                            command.smbShare
                                ?: throw IllegalArgumentException("SMB share is required for SMB storage"),
                        port = command.smbPort,
                        path = command.smbPath,
                        domain = command.smbDomain,
                        username = command.smbUsername,
                        password = password,
                    )
                }
            }
        val saved = saveStorageConfigPort.save(config)
        storageProviderConfig.refresh()
        return saved
    }

    @Transactional(readOnly = true)
    override fun test(): Boolean = storageProviderConfig.testConnection()
}
