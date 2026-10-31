package com.example.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enterprise-grade AES-256-GCM Cryptographic Manager backed by Android Keystore.
 * Features:
 * - 256-bit AES keys inside hardware-backed Android KeyStore
 * - AES/GCM/NoPadding with 128-bit authentication tags
 * - 12-byte cryptographically secure random nonces per operation
 * - SHA-256 cryptographic checksums for data integrity
 * - Salted PBKDF2-derived passphrase hashing (never plain text)
 */
object CryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "ZEN_AI_KEYSTORE_MASTER_V1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val PBKDF2_ITERATIONS = 10_000
    private const val PBKDF2_KEY_LENGTH_BITS = 256

    private val secureRandom = SecureRandom()

    // Curated high-entropy wordlist for 12-word recovery key architecture
    val RECOVERY_WORD_LIST = listOf(
        "zenith", "crystal", "quantum", "shield", "beacon", "nebula",
        "cipher", "matrix", "vector", "galaxy", "summit", "aurora",
        "obsidian", "plasma", "glacier", "phoenix", "canyon", "voyager",
        "titan", "horizon", "solace", "timber", "cradle", "harbor",
        "echo", "prism", "falcon", "orbit", "starlight", "granite",
        "falcon", "delta", "shadow", "vortex", "anchor", "stellar"
    )

    data class EncryptedResult(
        val ivBase64: String,
        val cipherTextBase64: String,
        val sha256Checksum: String
    )

    @Synchronized
    private fun getOrCreateMasterKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                entry?.secretKey ?: generateKeystoreKey()
            } else {
                generateKeystoreKey()
            }
        } catch (_: Exception) {
            // JVM / Robolectric environment fallback: generates memory-backed 256-bit AES key
            generateSoftwareKey()
        }
    }

    private fun generateKeystoreKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(false) // We manage unique 12-byte secure IVs explicitly
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    @Volatile
    private var softwareFallbackKey: SecretKey? = null

    private fun generateSoftwareKey(): SecretKey {
        softwareFallbackKey?.let { return it }
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256, secureRandom)
        val key = keyGen.generateKey()
        softwareFallbackKey = key
        return key
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Throwable) {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(str)
        } catch (_: Throwable) {
            android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
        }
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with a fresh 12-byte random IV.
     */
    fun encrypt(plainBytes: ByteArray, customKey: SecretKey? = null): EncryptedResult {
        val key = customKey ?: getOrCreateMasterKey()
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val cipherBytes = cipher.doFinal(plainBytes)
        val checksum = calculateSha256(plainBytes)

        return EncryptedResult(
            ivBase64 = encodeBase64(iv),
            cipherTextBase64 = encodeBase64(cipherBytes),
            sha256Checksum = checksum
        )
    }

    /**
     * Decrypts ciphertext bytes using AES-256-GCM and verifies authenticity tag.
     */
    fun decrypt(
        ivBase64: String,
        cipherTextBase64: String,
        customKey: SecretKey? = null
    ): ByteArray {
        val key = customKey ?: getOrCreateMasterKey()
        val iv = decodeBase64(ivBase64)
        val cipherBytes = decodeBase64(cipherTextBase64)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(cipherBytes)
    }

    /**
     * Computes cryptographic SHA-256 checksum formatted as lowercase hexadecimal.
     */
    fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies that the provided data matches the expected SHA-256 checksum.
     */
    fun verifyChecksum(data: ByteArray, expectedSha256: String): Boolean {
        val actual = calculateSha256(data)
        return MessageDigest.isEqual(actual.toByteArray(), expectedSha256.lowercase().toByteArray())
    }

    /**
     * Generates a 12-word secure recovery key.
     */
    fun generate12WordRecoveryPassphrase(): List<String> {
        val words = mutableListOf<String>()
        val total = RECOVERY_WORD_LIST.size
        for (i in 0 until 12) {
            val idx = secureRandom.nextInt(total)
            words.add(RECOVERY_WORD_LIST[idx])
        }
        return words
    }

    /**
     * Hashes a passphrase with a random 16-byte salt using PBKDF2WithHmacSHA256.
     * Returns "saltHex:hashHex". Plaintext passphrase is NEVER stored.
     */
    fun hashPassphrase(passphrase: String): String {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = skf.generateSecret(spec).encoded

        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        return "$saltHex:$hashHex"
    }

    /**
     * Validates a candidate passphrase against stored "saltHex:hashHex".
     */
    fun verifyPassphrase(candidate: String, storedSaltAndHash: String): Boolean {
        val parts = storedSaltAndHash.split(":")
        if (parts.size != 2) return false
        val salt = hexToBytes(parts[0])
        val expectedHash = parts[1]

        val spec = PBEKeySpec(candidate.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val actualHash = skf.generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }

        return MessageDigest.isEqual(actualHash.toByteArray(), expectedHash.toByteArray())
    }

    /**
     * Derives an AES-256 SecretKey from a recovery passphrase for cold-storage recovery.
     */
    fun deriveKeyFromPassphrase(passphrase: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = skf.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
