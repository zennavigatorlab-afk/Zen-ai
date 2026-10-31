package com.example.data.repository

import com.example.data.local.dao.MemoryDao
import com.example.data.local.entity.MemoryEntity
import com.example.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class MemoryRepository(private val memoryDao: MemoryDao) {

    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemoriesFlow()
    val memoryCount: Flow<Int> = memoryDao.getMemoryCountFlow()

    fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>> {
        return if (category == "All") {
            memoryDao.getAllMemoriesFlow()
        } else {
            memoryDao.getMemoriesByCategoryFlow(category)
        }
    }

    fun searchMemories(query: String): Flow<List<MemoryEntity>> {
        return if (query.isBlank()) {
            memoryDao.getAllMemoriesFlow()
        } else {
            memoryDao.searchMemoriesFlow(query.trim())
        }
    }

    suspend fun searchMemoriesList(query: String): List<MemoryEntity> {
        return if (query.isBlank()) {
            memoryDao.getAllMemories()
        } else {
            memoryDao.searchMemories(query.trim())
        }
    }

    suspend fun saveMemory(
        title: String,
        content: String,
        category: String,
        tags: String = "",
        isEncrypted: Boolean = false,
        existingId: Long = 0
    ): Long {
        val now = System.currentTimeMillis()
        val entity = MemoryEntity(
            id = existingId,
            title = title.trim(),
            content = content.trim(),
            category = category,
            tags = tags.trim(),
            createdAt = if (existingId != 0L) {
                memoryDao.getMemoryById(existingId)?.createdAt ?: now
            } else now,
            updatedAt = now,
            isEncrypted = isEncrypted
        )
        return memoryDao.insertMemory(entity)
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemoryById(id)
    }

    suspend fun clearAll() {
        memoryDao.clearAll()
    }

    suspend fun exportMemoriesJson(): String {
        val memories = memoryDao.getAllMemories()
        val root = JSONObject()
        root.put("type", "ZEN_AI_MEMORY_EXPORT")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("count", memories.size)

        val array = JSONArray()
        for (m in memories) {
            val item = JSONObject()
            item.put("title", m.title)
            item.put("content", m.content)
            item.put("category", m.category)
            item.put("tags", m.tags)
            item.put("createdAt", m.createdAt)
            item.put("updatedAt", m.updatedAt)
            item.put("isEncrypted", m.isEncrypted)
            array.put(item)
        }
        root.put("memories", array)
        val jsonStr = root.toString(2)
        val checksum = CryptoManager.calculateSha256(jsonStr.toByteArray(Charsets.UTF_8))
        root.put("sha256", checksum)
        return root.toString(2)
    }

    suspend fun importMemoriesJson(jsonStr: String): Int {
        val root = JSONObject(jsonStr)
        val memArray = root.optJSONArray("memories") ?: return 0
        var count = 0
        val now = System.currentTimeMillis()
        for (i in 0 until memArray.length()) {
            val item = memArray.getJSONObject(i)
            val entity = MemoryEntity(
                id = 0,
                title = item.getString("title"),
                content = item.getString("content"),
                category = item.optString("category", "General"),
                tags = item.optString("tags", ""),
                createdAt = item.optLong("createdAt", now),
                updatedAt = item.optLong("updatedAt", now),
                isEncrypted = item.optBoolean("isEncrypted", false)
            )
            memoryDao.insertMemory(entity)
            count++
        }
        return count
    }
}
