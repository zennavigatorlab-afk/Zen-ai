package com.example.security

import com.example.data.local.entity.AppSettingsEntity
import com.example.data.local.entity.BackupHistoryEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.MessageEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages atomic export, authenticated encryption, integrity verification,
 * and restoration of ZEN AI system data.
 */
object BackupIntegrityManager {
    const val BACKUP_FORMAT_VERSION = 1
    const val MAGIC_HEADER = "ZEN_AI_ENCRYPTED_VAULT"

    data class BackupPackage(
        val memories: List<MemoryEntity>,
        val conversations: List<ConversationEntity>,
        val messages: List<MessageEntity>,
        val documents: List<DocumentEntity>,
        val settings: List<AppSettingsEntity>
    )

    data class AuthenticatedBackupBundle(
        val header: String,
        val version: Int,
        val createdAt: Long,
        val plainSha256: String,
        val ivBase64: String,
        val encryptedPayloadBase64: String,
        val payloadSizeBytes: Long
    ) {
        fun toJsonString(): String {
            val json = JSONObject()
            json.put("header", header)
            json.put("version", version)
            json.put("createdAt", createdAt)
            json.put("plainSha256", plainSha256)
            json.put("ivBase64", ivBase64)
            json.put("encryptedPayloadBase64", encryptedPayloadBase64)
            json.put("payloadSizeBytes", payloadSizeBytes)
            return json.toString(2)
        }

        companion object {
            fun fromJsonString(jsonStr: String): AuthenticatedBackupBundle {
                val json = JSONObject(jsonStr)
                return AuthenticatedBackupBundle(
                    header = json.getString("header"),
                    version = json.getInt("version"),
                    createdAt = json.getLong("createdAt"),
                    plainSha256 = json.getString("plainSha256"),
                    ivBase64 = json.getString("ivBase64"),
                    encryptedPayloadBase64 = json.getString("encryptedPayloadBase64"),
                    payloadSizeBytes = json.getLong("payloadSizeBytes")
                )
            }
        }
    }

    /**
     * Serializes entities to JSON, encrypts using AES-256-GCM, generates SHA-256 checksums,
     * and packages into an authenticated bundle.
     */
    fun createAuthenticatedBackup(packageData: BackupPackage): Pair<AuthenticatedBackupBundle, BackupHistoryEntity> {
        val rootJson = JSONObject()
        rootJson.put("format_version", BACKUP_FORMAT_VERSION)
        rootJson.put("timestamp", System.currentTimeMillis())

        // 1. Memories
        val memArray = JSONArray()
        for (m in packageData.memories) {
            val item = JSONObject()
            item.put("id", m.id)
            item.put("title", m.title)
            item.put("content", m.content)
            item.put("category", m.category)
            item.put("tags", m.tags)
            item.put("createdAt", m.createdAt)
            item.put("updatedAt", m.updatedAt)
            item.put("isEncrypted", m.isEncrypted)
            memArray.put(item)
        }
        rootJson.put("memories", memArray)

        // 2. Conversations
        val convArray = JSONArray()
        for (c in packageData.conversations) {
            val item = JSONObject()
            item.put("id", c.id)
            item.put("title", c.title)
            item.put("createdAt", c.createdAt)
            item.put("updatedAt", c.updatedAt)
            convArray.put(item)
        }
        rootJson.put("conversations", convArray)

        // 3. Messages
        val msgArray = JSONArray()
        for (msg in packageData.messages) {
            val item = JSONObject()
            item.put("id", msg.id)
            item.put("conversationId", msg.conversationId)
            item.put("sender", msg.sender)
            item.put("content", msg.content)
            item.put("timestamp", msg.timestamp)
            item.put("metadata", msg.metadata)
            msgArray.put(item)
        }
        rootJson.put("messages", msgArray)

        // 4. Documents
        val docArray = JSONArray()
        for (d in packageData.documents) {
            val item = JSONObject()
            item.put("id", d.id)
            item.put("uri", d.uri)
            item.put("displayName", d.displayName)
            item.put("mimeType", d.mimeType)
            item.put("sizeBytes", d.sizeBytes)
            item.put("lastModified", d.lastModified)
            item.put("extractedText", d.extractedText)
            item.put("tags", d.tags)
            item.put("indexedAt", d.indexedAt)
            docArray.put(item)
        }
        rootJson.put("documents", docArray)

        // 5. Settings
        val setArray = JSONArray()
        for (s in packageData.settings) {
            val item = JSONObject()
            item.put("key", s.key)
            item.put("value", s.value)
            setArray.put(item)
        }
        rootJson.put("settings", setArray)

        val rawBytes = rootJson.toString().toByteArray(Charsets.UTF_8)
        val plainSha256 = CryptoManager.calculateSha256(rawBytes)

        val encryptedResult = CryptoManager.encrypt(rawBytes)
        val now = System.currentTimeMillis()

        val bundle = AuthenticatedBackupBundle(
            header = MAGIC_HEADER,
            version = BACKUP_FORMAT_VERSION,
            createdAt = now,
            plainSha256 = plainSha256,
            ivBase64 = encryptedResult.ivBase64,
            encryptedPayloadBase64 = encryptedResult.cipherTextBase64,
            payloadSizeBytes = rawBytes.size.toLong()
        )

        val history = BackupHistoryEntity(
            backupName = "zen_backup_${now}.zenvault",
            timestamp = now,
            sizeBytes = rawBytes.size.toLong(),
            checksumSha256 = plainSha256,
            status = "SUCCESS",
            isEncrypted = true,
            driveFileId = null
        )

        return Pair(bundle, history)
    }

    /**
     * Decrypts an authenticated backup bundle and verifies SHA-256 integrity checksum.
     * Throws SecurityException if checksum verification or cryptographic auth tag fails.
     */
    fun restoreAuthenticatedBackup(bundle: AuthenticatedBackupBundle): BackupPackage {
        if (bundle.header != MAGIC_HEADER) {
            throw SecurityException("Invalid backup format header: expected $MAGIC_HEADER")
        }

        val decryptedBytes = CryptoManager.decrypt(
            ivBase64 = bundle.ivBase64,
            cipherTextBase64 = bundle.encryptedPayloadBase64
        )

        val isValid = CryptoManager.verifyChecksum(decryptedBytes, bundle.plainSha256)
        if (!isValid) {
            throw SecurityException("Backup integrity verification failed: SHA-256 checksum mismatch!")
        }

        val jsonStr = String(decryptedBytes, Charsets.UTF_8)
        val root = JSONObject(jsonStr)

        val memories = mutableListOf<MemoryEntity>()
        val memArray = root.optJSONArray("memories") ?: JSONArray()
        for (i in 0 until memArray.length()) {
            val item = memArray.getJSONObject(i)
            memories.add(
                MemoryEntity(
                    id = 0, // Auto-generate clean IDs on restore
                    title = item.getString("title"),
                    content = item.getString("content"),
                    category = item.getString("category"),
                    tags = item.optString("tags", ""),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                    isEncrypted = item.optBoolean("isEncrypted", false)
                )
            )
        }

        val conversations = mutableListOf<ConversationEntity>()
        val convArray = root.optJSONArray("conversations") ?: JSONArray()
        for (i in 0 until convArray.length()) {
            val item = convArray.getJSONObject(i)
            conversations.add(
                ConversationEntity(
                    id = 0,
                    title = item.getString("title"),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        val messages = mutableListOf<MessageEntity>()
        val msgArray = root.optJSONArray("messages") ?: JSONArray()
        for (i in 0 until msgArray.length()) {
            val item = msgArray.getJSONObject(i)
            messages.add(
                MessageEntity(
                    id = 0,
                    conversationId = item.optLong("conversationId", 1),
                    sender = item.getString("sender"),
                    content = item.getString("content"),
                    timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                    metadata = item.optString("metadata", "")
                )
            )
        }

        val documents = mutableListOf<DocumentEntity>()
        val docArray = root.optJSONArray("documents") ?: JSONArray()
        for (i in 0 until docArray.length()) {
            val item = docArray.getJSONObject(i)
            documents.add(
                DocumentEntity(
                    id = 0,
                    uri = item.getString("uri"),
                    displayName = item.getString("displayName"),
                    mimeType = item.getString("mimeType"),
                    sizeBytes = item.optLong("sizeBytes", 0L),
                    lastModified = item.optLong("lastModified", System.currentTimeMillis()),
                    extractedText = item.optString("extractedText", ""),
                    tags = item.optString("tags", ""),
                    indexedAt = item.optLong("indexedAt", System.currentTimeMillis())
                )
            )
        }

        val settings = mutableListOf<AppSettingsEntity>()
        val setArray = root.optJSONArray("settings") ?: JSONArray()
        for (i in 0 until setArray.length()) {
            val item = setArray.getJSONObject(i)
            settings.add(
                AppSettingsEntity(
                    key = item.getString("key"),
                    value = item.getString("value")
                )
            )
        }

        return BackupPackage(memories, conversations, messages, documents, settings)
    }
}
