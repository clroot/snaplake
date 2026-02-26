package io.clroot.snaplake.adapter.outbound.persistence.adapter

import io.clroot.snaplake.application.port.outbound.EncryptionPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AesEncryptionAdapter @Autowired constructor(
    @Value("\${snaplake.encryption.key:}") private val configuredKey: String,
    @Value("\${spring.profiles.active:}") private val activeProfilesRaw: String = "",
) : EncryptionPort {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val KEY_LENGTH = 32
    }

    constructor(configuredKey: String, activeProfiles: Array<String>) : this(
        configuredKey = configuredKey,
        activeProfilesRaw = activeProfiles.joinToString(","),
    )

    private val secretKey: SecretKeySpec

    init {
        val keyBytes =
            if (configuredKey.isBlank()) {
                val activeProfiles = activeProfilesRaw.split(",").map { it.trim().lowercase() }
                if (activeProfiles.contains("dev")) {
                    log.warn("SNAPLAKE_ENCRYPTION_KEY is not set. Using default key. THIS IS NOT SAFE FOR PRODUCTION.")
                    "snaplake-default-encryption-key!".toByteArray()
                } else {
                    throw IllegalStateException(
                        "SNAPLAKE_ENCRYPTION_KEY must be set in production. " +
                            "Set it via environment variable or application.yml property 'snaplake.encryption.key'.",
                    )
                }
            } else {
                val decoded = configuredKey.toByteArray()
                require(decoded.size >= 16) { "Encryption key must be at least 16 bytes" }
                if (decoded.size < KEY_LENGTH) {
                    decoded.copyOf(KEY_LENGTH)
                } else {
                    decoded.copyOfRange(0, KEY_LENGTH)
                }
            }
        secretKey = SecretKeySpec(keyBytes, "AES")
    }

    override fun encrypt(plainText: String): String {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray())

        val combined = iv + encrypted
        return Base64.getEncoder().encodeToString(combined)
    }

    override fun decrypt(cipherText: String): String {
        val combined = Base64.getDecoder().decode(cipherText)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val decrypted = cipher.doFinal(encrypted)

        return String(decrypted)
    }
}
