package com.example.data.repository

import com.example.data.local.dao.AppSettingsDao
import com.example.data.local.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val appSettingsDao: AppSettingsDao) {

    suspend fun getSetting(key: String): String? {
        return appSettingsDao.get(key)
    }

    fun getSettingFlow(key: String): Flow<String?> {
        return appSettingsDao.getFlow(key)
    }

    suspend fun setSetting(key: String, value: String) {
        appSettingsDao.set(AppSettingsEntity(key = key, value = value))
    }

    suspend fun removeSetting(key: String) {
        appSettingsDao.delete(key)
    }
}
