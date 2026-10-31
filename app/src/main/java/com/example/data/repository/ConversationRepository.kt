package com.example.data.repository

import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    val allConversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversationsFlow()
    val conversationCount: Flow<Int> = conversationDao.getConversationCountFlow()
    val totalMessageCount: Flow<Int> = messageDao.getMessageCountFlow()

    fun getMessagesForConversation(conversationId: Long): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversationFlow(conversationId)
    }

    suspend fun getMessagesList(conversationId: Long): List<MessageEntity> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    suspend fun createConversation(title: String): Long {
        val now = System.currentTimeMillis()
        val conv = ConversationEntity(
            title = title.ifBlank { "Session ${System.currentTimeMillis() % 10000}" },
            createdAt = now,
            updatedAt = now
        )
        return conversationDao.insertConversation(conv)
    }

    suspend fun sendMessage(
        conversationId: Long,
        sender: String,
        content: String,
        metadata: String = ""
    ): Long {
        val now = System.currentTimeMillis()
        val message = MessageEntity(
            conversationId = conversationId,
            sender = sender,
            content = content,
            timestamp = now,
            metadata = metadata
        )
        val msgId = messageDao.insertMessage(message)

        // Update conversation timestamp
        val conv = conversationDao.getConversationById(conversationId)
        if (conv != null) {
            conversationDao.updateConversation(conv.copy(updatedAt = now))
        }
        return msgId
    }

    suspend fun deleteConversation(conversationId: Long) {
        messageDao.deleteMessagesForConversation(conversationId)
        conversationDao.deleteConversationById(conversationId)
    }

    suspend fun clearAll() {
        messageDao.clearAll()
        conversationDao.clearAll()
    }
}
