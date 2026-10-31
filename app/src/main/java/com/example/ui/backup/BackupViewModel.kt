package com.example.ui.backup

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ZenDatabase
import com.example.data.local.entity.AppSettingsEntity
import com.example.data.local.entity.BackupHistoryEntity
import com.example.data.repository.ConversationRepository
import com.example.data.repository.DocumentRepository
import com.example.data.repository.MemoryRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.VaultSyncRepository
import com.example.security.BackupIntegrityManager
import com.example.security.CryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ZenDatabase.getInstance(application)
    private val memoryRepo = MemoryRepository(db.memoryDao())
    private val convRepo = ConversationRepository(db.conversationDao(), db.messageDao())
    private val docRepo = DocumentRepository(db.documentDao())
    private val vaultSyncRepo = VaultSyncRepository(db.syncQueueDao(), db.backupHistoryDao())
    private val settingsRepo = SettingsRepository(db.appSettingsDao())

    val backupHistory: StateFlow<List<BackupHistoryEntity>> = vaultSyncRepo.backupHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestBackup: StateFlow<BackupHistoryEntity?> = vaultSyncRepo.latestBackup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _backupSchedule = MutableStateFlow("Manual")
    val backupSchedule: StateFlow<String> = _backupSchedule.asStateFlow()

    init {
        viewModelScope.launch {
            val freq = settingsRepo.getSetting("backup_frequency") ?: "Manual"
            _backupSchedule.value = freq
        }
    }

    fun setBackupSchedule(frequency: String) {
        viewModelScope.launch {
            _backupSchedule.value = frequency
            settingsRepo.setSetting("backup_frequency", frequency)
            _statusMessage.value = "Backup schedule updated to $frequency."
        }
    }

    fun executeManualBackup(context: Context) {
        viewModelScope.launch {
            _isOperating.value = true
            try {
                // 1. Gather all local entities
                val memories = db.memoryDao().getAllMemories()
                val convs = db.conversationDao().getAllConversationsFlow() // Or list
                val messages = db.messageDao().getRecentMessages(1000)
                val documents = db.documentDao().getAllDocuments()

                val packageData = BackupIntegrityManager.BackupPackage(
                    memories = memories,
                    conversations = emptyList(), // Store messages and memories
                    messages = messages,
                    documents = documents,
                    settings = listOf(
                        AppSettingsEntity("backup_frequency", _backupSchedule.value)
                    )
                )

                // 2. Encrypt & generate authenticated bundle
                val (bundle, historyEntity) = BackupIntegrityManager.createAuthenticatedBackup(packageData)

                // 3. Save to app files directory
                val backupDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
                val backupFile = File(backupDir, historyEntity.backupName)
                backupFile.writeText(bundle.toJsonString(), Charsets.UTF_8)

                // 4. Save history record in Room
                vaultSyncRepo.recordBackup(historyEntity)

                _statusMessage.value = "Backup created: ${historyEntity.backupName} (${historyEntity.sizeBytes} bytes, SHA-256 OK)"
            } catch (e: Exception) {
                _statusMessage.value = "Backup failed: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    fun restoreBackupBundle(bundleJson: String) {
        viewModelScope.launch {
            _isOperating.value = true
            try {
                val bundle = BackupIntegrityManager.AuthenticatedBackupBundle.fromJsonString(bundleJson)
                val restored = BackupIntegrityManager.restoreAuthenticatedBackup(bundle)

                // Insert into Room
                if (restored.memories.isNotEmpty()) {
                    db.memoryDao().insertMemories(restored.memories)
                }
                if (restored.documents.isNotEmpty()) {
                    db.documentDao().insertDocuments(restored.documents)
                }
                if (restored.messages.isNotEmpty()) {
                    db.messageDao().insertMessages(restored.messages)
                }

                _statusMessage.value = "Restored ${restored.memories.size} memories, ${restored.documents.size} documents with SHA-256 verification!"
            } catch (e: Exception) {
                _statusMessage.value = "Restore failed: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    fun deleteBackup(id: Long) {
        viewModelScope.launch {
            vaultSyncRepo.deleteBackup(id)
            _statusMessage.value = "Backup history entry deleted."
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
