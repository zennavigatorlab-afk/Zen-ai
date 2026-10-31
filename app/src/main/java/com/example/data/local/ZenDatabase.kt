package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AppSettingsDao
import com.example.data.local.dao.BackupHistoryDao
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.DocumentDao
import com.example.data.local.dao.MemoryDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.dao.SyncQueueEntityDao
import com.example.data.local.entity.AppSettingsEntity
import com.example.data.local.entity.BackupHistoryEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.SyncQueueEntity

@Database(
    entities = [
        MemoryEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        DocumentEntity::class,
        SyncQueueEntity::class,
        BackupHistoryEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ZenDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun documentDao(): DocumentDao
    abstract fun syncQueueDao(): SyncQueueEntityDao
    abstract fun backupHistoryDao(): BackupHistoryDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: ZenDatabase? = null

        fun getInstance(context: Context): ZenDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZenDatabase::class.java,
                    "zen_ai_vault.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
