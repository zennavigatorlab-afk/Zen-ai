package com.example.data.repository

import com.example.data.local.dao.DocumentDao
import com.example.data.local.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

class DocumentRepository(private val documentDao: DocumentDao) {

    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocumentsFlow()
    val documentCount: Flow<Int> = documentDao.getDocumentCountFlow()

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> {
        return if (query.isBlank()) {
            documentDao.getAllDocumentsFlow()
        } else {
            documentDao.searchDocumentsFlow(query.trim())
        }
    }

    suspend fun searchDocumentsList(query: String): List<DocumentEntity> {
        return if (query.isBlank()) {
            documentDao.getAllDocuments()
        } else {
            documentDao.searchDocuments(query.trim())
        }
    }

    suspend fun indexDocument(
        uri: String,
        displayName: String,
        mimeType: String,
        sizeBytes: Long,
        lastModified: Long,
        extractedText: String = "",
        tags: String = ""
    ): Long {
        val existing = documentDao.getDocumentByUri(uri)
        return if (existing != null) {
            val updated = existing.copy(
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                lastModified = lastModified,
                extractedText = if (extractedText.isNotBlank()) extractedText else existing.extractedText,
                tags = if (tags.isNotBlank()) tags else existing.tags,
                indexedAt = System.currentTimeMillis()
            )
            documentDao.updateDocument(updated)
            existing.id
        } else {
            val doc = DocumentEntity(
                uri = uri,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                lastModified = lastModified,
                extractedText = extractedText,
                tags = tags,
                indexedAt = System.currentTimeMillis()
            )
            documentDao.insertDocument(doc)
        }
    }

    suspend fun renameDocument(id: Long, newName: String) {
        val doc = documentDao.getDocumentById(id) ?: return
        documentDao.updateDocument(doc.copy(displayName = newName.trim()))
    }

    suspend fun deleteDocument(id: Long) {
        documentDao.deleteDocumentById(id)
    }

    suspend fun clearAll() {
        documentDao.clearAll()
    }
}
