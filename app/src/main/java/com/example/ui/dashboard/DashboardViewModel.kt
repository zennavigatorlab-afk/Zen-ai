package com.example.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ZenDatabase
import com.example.data.local.entity.BackupHistoryEntity
import com.example.data.repository.ConversationRepository
import com.example.data.repository.DocumentRepository
import com.example.data.repository.MemoryRepository
import com.example.data.repository.VaultSyncRepository
import com.example.phone.NetworkTelemetry
import com.example.phone.PhoneActionManager
import com.example.phone.StorageTelemetry
import com.example.vault.GoogleDriveVaultServiceImpl
import com.example.vault.VaultAuthStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ZenDatabase.getInstance(application)
    private val memoryRepo = MemoryRepository(db.memoryDao())
    private val convRepo = ConversationRepository(db.conversationDao(), db.messageDao())
    private val docRepo = DocumentRepository(db.documentDao())
    private val vaultSyncRepo = VaultSyncRepository(db.syncQueueDao(), db.backupHistoryDao())
    private val phoneManager = PhoneActionManager(application)
    private val vaultService = GoogleDriveVaultServiceImpl()

    private val _storage = MutableStateFlow(phoneManager.getStorageTelemetry())
    val storageTelemetry: StateFlow<StorageTelemetry> = _storage.asStateFlow()

    private val _network = MutableStateFlow(phoneManager.getNetworkTelemetry())
    val networkTelemetry: StateFlow<NetworkTelemetry> = _network.asStateFlow()

    val cloudStatus: StateFlow<VaultAuthStatus> = vaultService.authStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultAuthStatus.NOT_CONNECTED)

    val memoryCount: StateFlow<Int> = memoryRepo.memoryCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val conversationCount: StateFlow<Int> = convRepo.conversationCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val documentCount: StateFlow<Int> = docRepo.documentCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val latestBackup: StateFlow<BackupHistoryEntity?> = vaultSyncRepo.latestBackup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun refreshTelemetry() {
        viewModelScope.launch {
            _storage.value = phoneManager.getStorageTelemetry()
            _network.value = phoneManager.getNetworkTelemetry()
        }
    }
}
