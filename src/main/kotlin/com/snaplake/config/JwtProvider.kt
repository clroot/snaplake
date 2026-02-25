package com.snaplake.config

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*
import javax.crypto.spec.SecretKeySpec

@Component
class JwtProvider(
    @Value("\${snaplake.jwt.secret:}") private val secret: String,
    @Value("\${snaplake.jwt.expiration-hours:24}") private val expirationHours: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val signingKey: SecretKeySpec by lazy {
        val keyBytes = if (secret.isBlank()) {
            log.warn("JWT secret is not configured. Generating a random key. Tokens will be invalidated on restart.")
            val randomKey = ByteArray(64)
            java.security.SecureRandom().nextBytes(randomKey)
            randomKey
        } else {
            secret.toByteArray()
        }
        // Ensure at least 256-bit key for HS256
        val paddedKey = if (keyBytes.size < 32) {
            keyBytes.copyOf(32)
        } else {
            keyBytes
        }
        SecretKeySpec(paddedKey, "HmacSHA256")
    }

    fun generateToken(username: String): String {
        val now = Instant.now()
        val expiration = now.plusSeconds(expirationHours * 3600)

        return Jwts.builder()
            .subject(username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(signingKey)
            .compact()
    }

    fun validateTokenAndGetUsername(token: String): String? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload

            claims.subject
        } catch (e: JwtException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun getExpirationInstant(token: String): Instant? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload

            claims.expiration?.toInstant()
        } catch (e: Exception) {
            null
        }
    }
}
