package com.snaplake.application.service

import com.snaplake.application.port.inbound.GetSetupStatusUseCase
import com.snaplake.application.port.inbound.InitializeSystemUseCase
import com.snaplake.application.port.outbound.LoadUserPort
import com.snaplake.application.port.outbound.SaveStorageConfigPort
import com.snaplake.application.port.outbound.SaveUserPort
import com.snaplake.domain.exception.SystemAlreadyInitializedException
import com.snaplake.domain.model.StorageConfig
import com.snaplake.domain.model.StorageType
import com.snaplake.domain.model.User
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
) : InitializeSystemUseCase, GetSetupStatusUseCase {

    override fun initialize(command: InitializeSystemUseCase.Command) {
        if (loadUserPort.existsAny()) {
            throw SystemAlreadyInitializedException()
        }

        val passwordHash = passwordEncoder.encode(command.adminPassword)
        val user = User.create(
            username = command.adminUsername,
            passwordHash = passwordHash,
        )
        saveUserPort.save(user)

        val storageConfig = when (command.storageType) {
            StorageType.LOCAL -> StorageConfig.local(
                path = command.localPath ?: throw IllegalArgumentException("Local path is required for LOCAL storage"),
            )
            StorageType.S3 -> StorageConfig.s3(
                bucket = command.s3Bucket ?: throw IllegalArgumentException("S3 bucket is required for S3 storage"),
                region = command.s3Region ?: throw IllegalArgumentException("S3 region is required for S3 storage"),
                endpoint = command.s3Endpoint,
                accessKey = command.s3AccessKey,
                secretKey = command.s3SecretKey,
            )
        }
        saveStorageConfigPort.save(storageConfig)
    }

    @Transactional(readOnly = true)
    override fun getStatus(): GetSetupStatusUseCase.SetupStatus {
        return GetSetupStatusUseCase.SetupStatus(initialized = loadUserPort.existsAny())
    }
}
