package com.snaplake.adapter.outbound.persistence.adapter

import com.snaplake.application.port.outbound.EncryptionPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AesEncryptionAdapter(
    @Value("\${snaplake.encryption.key:}") private val configuredKey: String,
) : EncryptionPort {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val KEY_LENGTH = 32
    }

    private val secretKey: SecretKeySpec by lazy {
        val keyBytes = if (configuredKey.isBlank()) {
            log.warn("SNAPLAKE_ENCRYPTION_KEY is not set. Using default key. THIS IS NOT SAFE FOR PRODUCTION.")
            "snaplake-default-encryption-key!".toByteArray()
        } else {
            val decoded = configuredKey.toByteArray()
            if (decoded.size < KEY_LENGTH) {
                decoded.copyOf(KEY_LENGTH)
            } else {
                decoded.copyOfRange(0, KEY_LENGTH)
            }
        }
        SecretKeySpec(keyBytes, "AES")
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
