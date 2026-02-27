package io.clroot.snaplake.config

import io.clroot.snaplake.adapter.outbound.storage.CachingStorageProvider
import io.clroot.snaplake.adapter.outbound.storage.LocalStorageAdapter
import io.clroot.snaplake.adapter.outbound.storage.S3StorageAdapter
import io.clroot.snaplake.adapter.outbound.storage.SmbStorageAdapter
import io.clroot.snaplake.application.port.outbound.LoadStorageConfigPort
import io.clroot.snaplake.application.port.outbound.StorageProvider
import io.clroot.snaplake.domain.model.StorageType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class StorageProviderConfig(
    private val loadStorageConfigPort: LoadStorageConfigPort,
    @Value("\${snaplake.data-dir}") private val dataDir: String,
) : StorageProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var delegate: StorageProvider? = null

    fun refresh() {
        val current = delegate
        if (current is CachingStorageProvider) {
            current.clearCache()
        }
        delegate = null
        log.info("Storage provider configuration refreshed")
    }

    fun clearCache() {
        val current = getDelegate()
        if (current is CachingStorageProvider) {
            current.clearCache()
        }
    }

    fun getCacheInfo(): CachingStorageProvider.CacheInfo? {
        val current = getDelegate()
        return if (current is CachingStorageProvider) current.getCacheInfo() else null
    }

    @Synchronized
    private fun getDelegate(): StorageProvider {
        delegate?.let { return it }

        val config = loadStorageConfigPort.find()
        val provider =
            if (config == null) {
                log.info("No storage configuration found. Using default local storage: {}/snapshots", dataDir)
                LocalStorageAdapter("$dataDir/snapshots")
            } else {
                when (config.type) {
                    StorageType.LOCAL -> {
                        val path = config.localPath ?: "$dataDir/snapshots"
                        log.info("Using local storage: {}", path)
                        LocalStorageAdapter(path)
                    }

                    StorageType.S3 -> {
                        log.info("Using S3 storage: bucket={}, region={}", config.s3Bucket, config.s3Region)
                        val s3Adapter =
                            S3StorageAdapter.create(
                                bucket = config.s3Bucket!!,
                                region = config.s3Region!!,
                                endpoint = config.s3Endpoint,
                                accessKey = config.s3AccessKey,
                                secretKey = config.s3SecretKey,
                            )
                        val cacheDir = Path.of(dataDir, "cache", "snapshots")
                        log.info("S3 snapshot caching enabled: {}", cacheDir)
                        CachingStorageProvider(s3Adapter, cacheDir)
                    }

                    StorageType.SMB -> {
                        log.info(
                            "Using SMB storage: host={}, share={}, path={}",
                            config.smbHost,
                            config.smbShare,
                            config.smbPath,
                        )
                        val smbAdapter =
                            SmbStorageAdapter.create(
                                host = config.smbHost!!,
                                share = config.smbShare!!,
                                port = config.smbPort,
                                path = config.smbPath,
                                domain = config.smbDomain,
                                username = config.smbUsername,
                                password = config.smbPassword,
                            )
                        val cacheDir = Path.of(dataDir, "cache", "snapshots")
                        log.info("SMB snapshot caching enabled: {}", cacheDir)
                        CachingStorageProvider(smbAdapter, cacheDir)
                    }
                }
            }

        delegate = provider
        return provider
    }

    override fun write(
        path: String,
        data: ByteArray,
    ) = getDelegate().write(path, data)

    override fun read(path: String): ByteArray = getDelegate().read(path)

    override fun list(prefix: String): List<String> = getDelegate().list(prefix)

    override fun delete(path: String) = getDelegate().delete(path)

    override fun deleteAll(prefix: String) = getDelegate().deleteAll(prefix)

    override fun exists(path: String): Boolean = getDelegate().exists(path)

    override fun getUri(path: String): String = getDelegate().getUri(path)

    override fun testConnection(): Boolean = getDelegate().testConnection()

    override fun downloadToFile(
        path: String,
        destination: Path,
    ) = getDelegate().downloadToFile(path, destination)
}
