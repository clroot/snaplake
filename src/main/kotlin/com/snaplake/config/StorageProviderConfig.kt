package com.snaplake.config

import com.snaplake.adapter.outbound.storage.LocalStorageAdapter
import com.snaplake.adapter.outbound.storage.S3StorageAdapter
import com.snaplake.application.port.outbound.LoadStorageConfigPort
import com.snaplake.application.port.outbound.StorageProvider
import com.snaplake.domain.model.StorageType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class StorageProviderConfig(
    private val loadStorageConfigPort: LoadStorageConfigPort,
    @Value("\${snaplake.data-dir}") private val dataDir: String,
) : StorageProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var delegate: StorageProvider? = null

    fun refresh() {
        delegate = null
        log.info("Storage provider configuration refreshed")
    }

    @Synchronized
    private fun getDelegate(): StorageProvider {
        delegate?.let { return it }

        val config = loadStorageConfigPort.find()
        val provider = if (config == null) {
            log.info("No storage configuration found. Using default local storage: {}/snapshots", dataDir)
            LocalStorageAdapter("$dataDir/snapshots")
        } else when (config.type) {
            StorageType.LOCAL -> {
                val path = config.localPath ?: "$dataDir/snapshots"
                log.info("Using local storage: {}", path)
                LocalStorageAdapter(path)
            }
            StorageType.S3 -> {
                log.info("Using S3 storage: bucket={}, region={}", config.s3Bucket, config.s3Region)
                S3StorageAdapter.create(
                    bucket = config.s3Bucket!!,
                    region = config.s3Region!!,
                    endpoint = config.s3Endpoint,
                    accessKey = config.s3AccessKey,
                    secretKey = config.s3SecretKey,
                )
            }
        }

        delegate = provider
        return provider
    }

    override fun write(path: String, data: ByteArray) = getDelegate().write(path, data)
    override fun read(path: String): ByteArray = getDelegate().read(path)
    override fun list(prefix: String): List<String> = getDelegate().list(prefix)
    override fun delete(path: String) = getDelegate().delete(path)
    override fun deleteAll(prefix: String) = getDelegate().deleteAll(prefix)
    override fun exists(path: String): Boolean = getDelegate().exists(path)
    override fun getUri(path: String): String = getDelegate().getUri(path)
    override fun testConnection(): Boolean = getDelegate().testConnection()
}
