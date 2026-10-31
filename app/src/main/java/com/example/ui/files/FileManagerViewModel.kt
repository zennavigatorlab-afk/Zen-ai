package com.example.ui.files

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ZenDatabase
import com.example.data.local.entity.DocumentEntity
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalCoroutinesApi::class)
class FileManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ZenDatabase.getInstance(application)
    private val documentRepo = DocumentRepository(db.documentDao())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val documents: StateFlow<List<DocumentEntity>> = _searchQuery
        .flatMapLatest { query ->
            documentRepo.searchDocuments(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    fun importDocumentFromUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                // Try taking persistable URI permission
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Ignored if temporary grant
                }

                var displayName = "Document_${System.currentTimeMillis() % 10000}"
                var sizeBytes = 0L
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: displayName
                        if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                    }
                }

                // Extract text preview if readable format
                var extractedText = ""
                if (mimeType.startsWith("text/") || displayName.endsWith(".txt") ||
                    displayName.endsWith(".md") || displayName.endsWith(".json") || displayName.endsWith(".csv")) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val reader = BufferedReader(InputStreamReader(stream))
                            val sb = java.lang.StringBuilder()
                            var line: String?
                            var count = 0
                            while (reader.readLine().also { line = it } != null && count < 20) {
                                sb.append(line).append("\n")
                                count++
                            }
                            extractedText = sb.toString().take(2000)
                        }
                    } catch (_: Exception) {
                        extractedText = "[Binary or unreadable stream]"
                    }
                }

                val docId = documentRepo.indexDocument(
                    uri = uri.toString(),
                    displayName = displayName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    lastModified = System.currentTimeMillis(),
                    extractedText = extractedText,
                    tags = "saf_import"
                )

                _statusMessage.value = "Imported & indexed: $displayName ($docId)"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to import file: ${e.message}"
            }
        }
    }

    fun renameDocument(id: Long, newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                documentRepo.renameDocument(id, newName)
                _statusMessage.value = "File renamed."
            }
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            documentRepo.deleteDocument(id)
            _statusMessage.value = "Document index removed."
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
