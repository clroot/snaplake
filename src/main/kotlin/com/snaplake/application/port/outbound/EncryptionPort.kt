package com.snaplake.application.port.outbound

interface EncryptionPort {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}
