package com.example.data.repository

import com.example.data.local.dao.BackupHistoryDao
import com.example.data.local.dao.SyncQueueEntityDao
import com.example.data.local.entity.BackupHistoryEntity
import com.example.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

class VaultSyncRepository(
    private val syncQueueDao: SyncQueueEntityDao,
    private val backupHistoryDao: BackupHistoryDao
) {
    val syncQueue: Flow<List<SyncQueueEntity>> = syncQueueDao.getAllQueueFlow()
    val pendingCount: Flow<Int> = syncQueueDao.getPendingCountFlow()
    val backupHistory: Flow<List<BackupHistoryEntity>> = backupHistoryDao.getAllBackupsFlow()
    val latestBackup: Flow<BackupHistoryEntity?> = backupHistoryDao.getLatestBackupFlow()

    suspend fun enqueueItem(entityType: String, entityId: Long, operation: String): Long {
        val item = SyncQueueEntity(
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            status = "PENDING",
            retryCount = 0,
            lastAttempt = System.currentTimeMillis()
        )
        return syncQueueDao.enqueue(item)
    }

    suspend fun markItemStatus(id: Long, status: String, error: String? = null) {
        val item = syncQueueDao.getAllQueueFlow() // Or update directly
        // Update retry or state
    }

    suspend fun recordBackup(backup: BackupHistoryEntity): Long {
        return backupHistoryDao.insert(backup)
    }

    suspend fun deleteBackup(id: Long) {
        backupHistoryDao.delete(id)
    }

    suspend fun clearCompletedQueue() {
        syncQueueDao.clearCompleted()
    }

    suspend fun clearAll() {
        syncQueueDao.clearAll()
        backupHistoryDao.clearAll()
    }
}
