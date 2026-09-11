package com.railway.ticketsystem.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PasswordDigest(val hash: String, val salt: String)

/** PBKDF2 password hashes; the password itself is never persisted. */
object PasswordHasher {
    private const val ITERATIONS = 210_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private val random = SecureRandom()

    fun hash(password: String): PasswordDigest {
        require(password.isNotBlank()) { "Password cannot be blank" }
        val salt = ByteArray(SALT_LENGTH_BYTES).also(random::nextBytes)
        val hash = derive(password, salt)
        return PasswordDigest(hash.toHex(), salt.toHex())
    }

    fun verify(password: String, hash: String?, salt: String?): Boolean {
        if (password.isBlank() || hash.isNullOrBlank() || salt.isNullOrBlank()) return false
        return runCatching {
            val expected = hash.hexToBytes()
            val actual = derive(password, salt.hexToBytes())
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val chars = password.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)
            try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex value" }
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
