package com.snaplake.adapter.outbound.storage

import com.snaplake.application.port.outbound.StorageProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.net.URI

class S3StorageAdapter(
    private val s3Client: S3Client,
    private val bucket: String,
) : StorageProvider {

    override fun write(path: String, data: ByteArray) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .build(),
            RequestBody.fromBytes(data),
        )
    }

    override fun read(path: String): ByteArray {
        val response = s3Client.getObjectAsBytes(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .build(),
        )
        return response.asByteArray()
    }

    override fun list(prefix: String): List<String> {
        val result = mutableListOf<String>()
        var continuationToken: String? = null

        do {
            val request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .apply { if (continuationToken != null) continuationToken(continuationToken) }
                .build()

            val response = s3Client.listObjectsV2(request)
            response.contents().forEach { obj ->
                result.add(obj.key())
            }
            continuationToken = if (response.isTruncated) response.nextContinuationToken() else null
        } while (continuationToken != null)

        return result
    }

    override fun delete(path: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .build(),
        )
    }

    override fun deleteAll(prefix: String) {
        val keys = list(prefix)
        if (keys.isEmpty()) return

        keys.chunked(1000).forEach { batch ->
            val deleteObjects = batch.map { ObjectIdentifier.builder().key(it).build() }
            s3Client.deleteObjects(
                DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(deleteObjects).build())
                    .build(),
            )
        }
    }

    override fun exists(path: String): Boolean {
        return try {
            s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(path)
                    .build(),
            )
            true
        } catch (e: NoSuchKeyException) {
            false
        }
    }

    override fun getUri(path: String): String {
        return "s3://$bucket/$path"
    }

    override fun testConnection(): Boolean {
        return try {
            s3Client.headBucket(
                HeadBucketRequest.builder()
                    .bucket(bucket)
                    .build(),
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        fun create(
            bucket: String,
            region: String,
            endpoint: String?,
            accessKey: String?,
            secretKey: String?,
        ): S3StorageAdapter {
            val builder = S3Client.builder()
                .region(Region.of(region))

            if (endpoint != null) {
                builder.endpointOverride(URI.create(endpoint))
                builder.forcePathStyle(true)
            }

            if (accessKey != null && secretKey != null) {
                builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey),
                    )
                )
            } else {
                builder.credentialsProvider(DefaultCredentialsProvider.create())
            }

            return S3StorageAdapter(builder.build(), bucket)
        }
    }
}
