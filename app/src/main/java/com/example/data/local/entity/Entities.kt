package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memories",
    indices = [Index(value = ["category"]), Index(value = ["createdAt"])]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val category: String, // e.g. "Work", "Personal", "Health", "Preferences", "Ideas"
    val tags: String = "", // Comma-separated tags
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = false
)

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["updatedAt"])]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId"]), Index(value = ["timestamp"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val sender: String, // "USER" or "ZEN"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: String = "" // e.g. tokens, retrieved context, citations
)

@Entity(
    tableName = "documents",
    indices = [Index(value = ["uri"], unique = true), Index(value = ["indexedAt"])]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val extractedText: String = "",
    val tags: String = "",
    val indexedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["status"]), Index(value = ["lastAttempt"])]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityType: String, // "MEMORY", "DOCUMENT", "BACKUP"
    val entityId: Long,
    val operation: String, // "UPLOAD", "DOWNLOAD", "DELETE"
    val status: String = "PENDING", // "PENDING", "IN_PROGRESS", "FAILED"
    val retryCount: Int = 0,
    val lastAttempt: Long = System.currentTimeMillis(),
    val error: String? = null
)

@Entity(
    tableName = "backup_history",
    indices = [Index(value = ["timestamp"])]
)
data class BackupHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val backupName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sizeBytes: Long,
    val checksumSha256: String,
    val status: String = "SUCCESS", // "SUCCESS", "FAILED"
    val isEncrypted: Boolean = true,
    val driveFileId: String? = null
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
