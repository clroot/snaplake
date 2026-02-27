package io.clroot.snaplake.application.service

import io.clroot.snaplake.application.port.inbound.GetSetupStatusUseCase
import io.clroot.snaplake.application.port.inbound.InitializeSystemUseCase
import io.clroot.snaplake.application.port.outbound.LoadUserPort
import io.clroot.snaplake.application.port.outbound.SaveStorageConfigPort
import io.clroot.snaplake.application.port.outbound.SaveUserPort
import io.clroot.snaplake.domain.exception.SystemAlreadyInitializedException
import io.clroot.snaplake.domain.model.StorageConfig
import io.clroot.snaplake.domain.model.StorageType
import io.clroot.snaplake.domain.model.User
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SetupService(
    private val loadUserPort: LoadUserPort,
    private val saveUserPort: SaveUserPort,
    private val saveStorageConfigPort: SaveStorageConfigPort,
    private val passwordEncoder: Argon2PasswordEncoder,
) : InitializeSystemUseCase,
    GetSetupStatusUseCase {
    override fun initialize(command: InitializeSystemUseCase.Command) {
        if (loadUserPort.existsAny()) {
            throw SystemAlreadyInitializedException()
        }

        val passwordHash = passwordEncoder.encode(command.adminPassword)
        val user =
            User.create(
                username = command.adminUsername,
                passwordHash = passwordHash,
            )
        saveUserPort.save(user)

        val storageConfig =
            when (command.storageType) {
                StorageType.LOCAL -> {
                    StorageConfig.local(
                        path = command.localPath ?: throw IllegalArgumentException("Local path is required for LOCAL storage"),
                    )
                }

                StorageType.S3 -> {
                    StorageConfig.s3(
                        bucket = command.s3Bucket ?: throw IllegalArgumentException("S3 bucket is required for S3 storage"),
                        region = command.s3Region ?: throw IllegalArgumentException("S3 region is required for S3 storage"),
                        endpoint = command.s3Endpoint,
                        accessKey = command.s3AccessKey,
                        secretKey = command.s3SecretKey,
                    )
                }

                StorageType.SMB -> {
                    StorageConfig.smb(
                        host = command.smbHost ?: throw IllegalArgumentException("SMB host is required for SMB storage"),
                        share = command.smbShare ?: throw IllegalArgumentException("SMB share is required for SMB storage"),
                        port = command.smbPort,
                        path = command.smbPath,
                        domain = command.smbDomain,
                        username = command.smbUsername,
                        password = command.smbPassword,
                    )
                }
            }
        saveStorageConfigPort.save(storageConfig)
    }

    @Transactional(readOnly = true)
    override fun getStatus(): GetSetupStatusUseCase.SetupStatus = GetSetupStatusUseCase.SetupStatus(initialized = loadUserPort.existsAny())
}
