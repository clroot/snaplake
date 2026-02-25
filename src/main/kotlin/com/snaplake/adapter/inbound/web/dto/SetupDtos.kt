package com.snaplake.adapter.inbound.web.dto

data class InitializeRequest(
    val adminUsername: String,
    val adminPassword: String,
    val storageType: String,
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
    val storageType: String,
    val localPath: String?,
    val s3Bucket: String?,
    val s3Region: String?,
    val s3Endpoint: String?,
    val s3AccessKey: String?,
    val s3SecretKey: String?,
)
