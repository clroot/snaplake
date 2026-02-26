package io.clroot.snaplake.adapter.outbound.persistence.migration

import io.clroot.snaplake.adapter.outbound.persistence.repository.StorageConfigJpaRepository
import io.clroot.snaplake.application.port.outbound.EncryptionPort
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class StorageConfigMigrationRunner(
    private val repository: StorageConfigJpaRepository,
    private val encryptionPort: EncryptionPort,
) : ApplicationRunner {
    companion object {
        private const val ENCRYPTED_PREFIX = "ENC:"
    }

    override fun run(args: ApplicationArguments) {
        val entity = repository.findById(1).orElse(null) ?: return
        var updated = false

        if (entity.s3AccessKey != null && !entity.s3AccessKey!!.startsWith(ENCRYPTED_PREFIX)) {
            entity.s3AccessKey = ENCRYPTED_PREFIX + encryptionPort.encrypt(entity.s3AccessKey!!)
            updated = true
        }
        if (entity.s3SecretKey != null && !entity.s3SecretKey!!.startsWith(ENCRYPTED_PREFIX)) {
            entity.s3SecretKey = ENCRYPTED_PREFIX + encryptionPort.encrypt(entity.s3SecretKey!!)
            updated = true
        }

        if (updated) repository.save(entity)
    }
}
