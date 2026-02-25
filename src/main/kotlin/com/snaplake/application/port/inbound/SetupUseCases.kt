package com.snaplake.application.port.inbound

import com.snaplake.domain.model.StorageType

interface InitializeSystemUseCase {
    fun initialize(command: Command)

    data class Command(
        val adminUsername: String,
        val adminPassword: String,
        val storageType: StorageType,
        val localPath: String?,
        val s3Bucket: String?,
        val s3Region: String?,
        val s3Endpoint: String?,
        val s3AccessKey: String?,
        val s3SecretKey: String?,
    )
}

interface GetSetupStatusUseCase {
    fun getStatus(): SetupStatus

    data class SetupStatus(val initialized: Boolean)
}
