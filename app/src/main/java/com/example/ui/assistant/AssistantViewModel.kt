package com.example.ui.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.LocalIntelligenceEngine
import com.example.data.local.ZenDatabase
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.repository.ConversationRepository
import com.example.data.repository.DocumentRepository
import com.example.data.repository.MemoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ZenDatabase.getInstance(application)
    private val memoryRepo = MemoryRepository(db.memoryDao())
    private val docRepo = DocumentRepository(db.documentDao())
    private val convRepo = ConversationRepository(db.conversationDao(), db.messageDao())
    private val localEngine = LocalIntelligenceEngine(memoryRepo, docRepo)

    private val _activeConversationId = MutableStateFlow<Long?>(null)
    val activeConversationId: StateFlow<Long?> = _activeConversationId.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    val conversations: StateFlow<List<ConversationEntity>> = convRepo.allConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMessages: StateFlow<List<MessageEntity>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId != null) {
                convRepo.getMessagesForConversation(convId)
            } else {
                MutableStateFlow(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        initializeDefaultConversation()
    }

    private fun initializeDefaultConversation() {
        viewModelScope.launch {
            val list = convRepo.getMessagesList(1) // check if default exists
            if (_activeConversationId.value == null) {
                val newId = convRepo.createConversation("ZEN Offline Session")
                _activeConversationId.value = newId
                // Welcome message
                convRepo.sendMessage(
                    conversationId = newId,
                    sender = "ZEN",
                    content = "ZEN AI Local Intelligence is online. All processing runs completely on-device without cloud LLMs or API keys. Your personal memory and indexed documents are available offline.",
                    metadata = "100% On-Device"
                )
            }
        }
    }

    fun startNewSession(title: String = "ZEN Session") {
        viewModelScope.launch {
            val newId = convRepo.createConversation(title)
            _activeConversationId.value = newId
            convRepo.sendMessage(
                conversationId = newId,
                sender = "ZEN",
                content = "Started new offline session. How can I assist you with your personal knowledge base?",
                metadata = "Local"
            )
        }
    }

    fun selectConversation(id: Long) {
        _activeConversationId.value = id
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            convRepo.deleteConversation(id)
            if (_activeConversationId.value == id) {
                _activeConversationId.value = null
                initializeDefaultConversation()
            }
        }
    }

    fun sendMessage(userText: String) {
        val query = userText.trim()
        if (query.isBlank()) return

        val convId = _activeConversationId.value ?: return

        viewModelScope.launch {
            _isProcessing.value = true
            // 1. Record user message
            convRepo.sendMessage(
                conversationId = convId,
                sender = "USER",
                content = query
            )

            // 2. Process via LocalIntelligenceEngine
            val result = localEngine.processQuery(query)

            // 3. Record AI message
            convRepo.sendMessage(
                conversationId = convId,
                sender = "ZEN",
                content = result.replyText,
                metadata = "${result.modelArchitecture} • Intent: ${result.intent.name}"
            )
            _isProcessing.value = false
        }
    }
}
