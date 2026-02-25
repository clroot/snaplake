package com.snaplake.domain.model

enum class StorageType { LOCAL, S3 }

class StorageConfig private constructor(
    val type: StorageType,
    val localPath: String?,
    val s3Bucket: String?,
    val s3Region: String?,
    val s3Endpoint: String?,
    val s3AccessKey: String?,
    val s3SecretKey: String?,
) {
    companion object {
        fun local(path: String): StorageConfig {
            require(path.isNotBlank()) { "Local path must not be blank" }
            return StorageConfig(
                type = StorageType.LOCAL,
                localPath = path,
                s3Bucket = null,
                s3Region = null,
                s3Endpoint = null,
                s3AccessKey = null,
                s3SecretKey = null,
            )
        }

        fun s3(
            bucket: String,
            region: String,
            endpoint: String? = null,
            accessKey: String? = null,
            secretKey: String? = null,
        ): StorageConfig {
            require(bucket.isNotBlank()) { "S3 bucket must not be blank" }
            require(region.isNotBlank()) { "S3 region must not be blank" }
            return StorageConfig(
                type = StorageType.S3,
                localPath = null,
                s3Bucket = bucket,
                s3Region = region,
                s3Endpoint = endpoint,
                s3AccessKey = accessKey,
                s3SecretKey = secretKey,
            )
        }

        fun reconstitute(
            type: StorageType,
            localPath: String?,
            s3Bucket: String?,
            s3Region: String?,
            s3Endpoint: String?,
            s3AccessKey: String?,
            s3SecretKey: String?,
        ): StorageConfig = StorageConfig(
            type, localPath, s3Bucket, s3Region, s3Endpoint, s3AccessKey, s3SecretKey,
        )
    }
}
