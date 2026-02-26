package io.clroot.snaplake.adapter.inbound.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class InitializeRequest(
    @field:NotBlank val adminUsername: String,
    @field:NotBlank @field:Size(min = 4) val adminPassword: String,
    @field:NotBlank val storageType: String,
    val localPath: String?,
    val s3Bucket: String?,
    val s3Region: String?,
    val s3Endpoint: String?,
    val s3AccessKey: String?,
    val s3SecretKey: String?,
)

data class SetupStatusResponse(
    val initialized: Boolean,
)

data class TestStorageRequest(
    @field:NotBlank val storageType: String,
    val localPath: String?,
    val s3Bucket: String?,
    val s3Region: String?,
    val s3Endpoint: String?,
    val s3AccessKey: String?,
    val s3SecretKey: String?,
)
