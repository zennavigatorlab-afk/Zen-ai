package com.example.ui.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ZenDatabase
import com.example.data.local.entity.MemoryEntity
import com.example.data.repository.MemoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ZenDatabase.getInstance(application)
    private val repository = MemoryRepository(db.memoryDao())

    val categories = listOf("All", "Preferences", "Work", "Personal", "Health", "Ideas", "General")

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val memories: StateFlow<List<MemoryEntity>> = combine(
        _selectedCategory,
        _searchQuery
    ) { category, query ->
        Pair(category, query)
    }.flatMapLatest { (category, query) ->
        if (query.isNotBlank()) {
            repository.searchMemories(query)
        } else {
            repository.getMemoriesByCategory(category)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(cat: String) {
        _selectedCategory.value = cat
    }

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    fun saveMemory(
        title: String,
        content: String,
        category: String,
        tags: String = "",
        isEncrypted: Boolean = false,
        existingId: Long = 0
    ) {
        viewModelScope.launch {
            if (title.isBlank() || content.isBlank()) {
                _statusMessage.value = "Title and content cannot be empty."
                return@launch
            }
            repository.saveMemory(
                title = title,
                content = content,
                category = category,
                tags = tags,
                isEncrypted = isEncrypted,
                existingId = existingId
            )
            _statusMessage.value = if (existingId != 0L) "Memory updated." else "Memory saved securely."
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
            _statusMessage.value = "Memory deleted."
        }
    }

    suspend fun exportMemoriesJson(): String {
        return repository.exportMemoriesJson()
    }

    fun importMemoriesJson(json: String) {
        viewModelScope.launch {
            try {
                val count = repository.importMemoriesJson(json)
                _statusMessage.value = "Successfully imported $count memories."
            } catch (e: Exception) {
                _statusMessage.value = "Import failed: ${e.message}"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
