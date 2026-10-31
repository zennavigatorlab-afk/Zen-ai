package com.example

import com.example.data.local.entity.AppSettingsEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.MemoryEntity
import com.example.security.BackupIntegrityManager
import com.example.security.CryptoManager
import com.example.vault.ConflictAction
import com.example.vault.GoogleDriveVaultServiceImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenSecurityAndArchitectureTest {

    @Test
    fun testAes256GcmEncryptionAndDecryption() {
        val originalText = "ZEN_AI_CONFIDENTIAL_PAYLOAD_12345"
        val plainBytes = originalText.toByteArray(Charsets.UTF_8)

        // Encrypt with fresh random 12-byte IV
        val encrypted = CryptoManager.encrypt(plainBytes)
        assertNotNull(encrypted.ivBase64)
        assertNotNull(encrypted.cipherTextBase64)
        assertNotNull(encrypted.sha256Checksum)

        // Decrypt
        val decryptedBytes = CryptoManager.decrypt(
            ivBase64 = encrypted.ivBase64,
            cipherTextBase64 = encrypted.cipherTextBase64
        )
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)

        assertEquals(originalText, decryptedText)
    }

    @Test
    fun testSha256ChecksumIntegrity() {
        val data = "Secure User Memory Note".toByteArray(Charsets.UTF_8)
        val checksum = CryptoManager.calculateSha256(data)
        assertTrue(CryptoManager.verifyChecksum(data, checksum))

        val tamperedData = "Tampered User Memory Note".toByteArray(Charsets.UTF_8)
        assertFalse(CryptoManager.verifyChecksum(tamperedData, checksum))
    }

    @Test
    fun testPassphraseSaltAndHash() {
        val recoveryPhrase = "zenith crystal shield matrix aurora obsidian"
        val hashed = CryptoManager.hashPassphrase(recoveryPhrase)

        // Never stored in plain text
        assertFalse(hashed.contains("zenith"))
        assertTrue(hashed.contains(":"))

        // Verification
        assertTrue(CryptoManager.verifyPassphrase(recoveryPhrase, hashed))
        assertFalse(CryptoManager.verifyPassphrase("wrong passphrase", hashed))
    }

    @Test
    fun test12WordRecoveryKeyGeneration() {
        val words = CryptoManager.generate12WordRecoveryPassphrase()
        assertEquals(12, words.size)
        words.forEach { word ->
            assertTrue(word.isNotBlank())
            assertTrue(CryptoManager.RECOVERY_WORD_LIST.contains(word))
        }
    }

    @Test
    fun testAuthenticatedBackupPackageCycle() {
        val memories = listOf(
            MemoryEntity(
                id = 1,
                title = "Meeting Preferences",
                content = "Async preferred, no morning meetings",
                category = "Preferences"
            )
        )
        val docs = listOf(
            DocumentEntity(
                id = 1,
                uri = "content://saf/doc1",
                displayName = "system_architecture.md",
                mimeType = "text/markdown",
                sizeBytes = 1024,
                lastModified = System.currentTimeMillis(),
                extractedText = "ZEN AI is built with local SQLite Room"
            )
        )
        val pkg = BackupIntegrityManager.BackupPackage(
            memories = memories,
            conversations = emptyList(),
            messages = emptyList(),
            documents = docs,
            settings = listOf(AppSettingsEntity("backup_frequency", "Daily"))
        )

        val (bundle, history) = BackupIntegrityManager.createAuthenticatedBackup(pkg)
        assertEquals(BackupIntegrityManager.MAGIC_HEADER, bundle.header)
        assertTrue(history.isEncrypted)

        // Restore & verify
        val restoredPkg = BackupIntegrityManager.restoreAuthenticatedBackup(bundle)
        assertEquals(1, restoredPkg.memories.size)
        assertEquals("Meeting Preferences", restoredPkg.memories[0].title)
        assertEquals(1, restoredPkg.documents.size)
        assertEquals("system_architecture.md", restoredPkg.documents[0].displayName)
    }

    @Test
    fun testGoogleDriveConflictDetection() {
        val vaultService = GoogleDriveVaultServiceImpl()
        val now = System.currentTimeMillis()

        // Same time
        val synced = vaultService.analyzeConflict(now, now)
        assertFalse(synced.hasConflict)

        // Local newer
        val localNewer = vaultService.analyzeConflict(now + 10000, now)
        assertTrue(localNewer.hasConflict)
        assertEquals(ConflictAction.USE_LOCAL, localNewer.recommendedAction)

        // Remote newer
        val remoteNewer = vaultService.analyzeConflict(now, now + 10000)
        assertTrue(remoteNewer.hasConflict)
        assertEquals(ConflictAction.USE_REMOTE, remoteNewer.recommendedAction)
    }
}
