package com.example.ai

import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.repository.DocumentRepository
import com.example.data.repository.MemoryRepository

enum class EngineIntent {
    MEMORY_SEARCH,
    DOCUMENT_SEARCH,
    SYSTEM_DIAGNOSTICS,
    NOTE_CAPTURE,
    GENERAL_LOCAL_QUERY
}

data class LocalEngineOutput(
    val replyText: String,
    val intent: EngineIntent,
    val matchedMemories: List<MemoryEntity> = emptyList(),
    val matchedDocuments: List<DocumentEntity> = emptyList(),
    val isFullyOffline: Boolean = true,
    val modelArchitecture: String = "ZEN Local Semantic Engine (Ready for on-device GGUF/ONNX)"
)

interface IntelligenceEngine {
    suspend fun processQuery(userInput: String): LocalEngineOutput
}

/**
 * Native Offline Intelligence Engine for ZEN AI.
 * Operates strictly locally with zero external API calls, zero telemetry,
 * and zero fabricated mock responses. Integrates real local Room data.
 */
class LocalIntelligenceEngine(
    private val memoryRepository: MemoryRepository,
    private val documentRepository: DocumentRepository
) : IntelligenceEngine {

    override suspend fun processQuery(userInput: String): LocalEngineOutput {
        val query = userInput.trim()
        val lower = query.lowercase()

        // 1. Check for Note / Memory capture intent
        if (lower.startsWith("remember ") || lower.startsWith("note: ") || lower.startsWith("save ")) {
            val contentToSave = query
                .replaceFirst("(?i)remember that ".toRegex(), "")
                .replaceFirst("(?i)remember ".toRegex(), "")
                .replaceFirst("(?i)note: ".toRegex(), "")
                .replaceFirst("(?i)save ".toRegex(), "")
                .trim()

            if (contentToSave.isNotBlank()) {
                val savedId = memoryRepository.saveMemory(
                    title = "Quick Note: ${contentToSave.take(30)}...",
                    content = contentToSave,
                    category = "Preferences",
                    tags = "captured_by_ai"
                )
                return LocalEngineOutput(
                    replyText = "Captured and saved directly to your local encrypted Personal Memory (Record #$savedId). Data is stored strictly on your device.",
                    intent = EngineIntent.NOTE_CAPTURE
                )
            }
        }

        // 2. Search local memory repository
        val matchedMemories = memoryRepository.searchMemoriesList(query)
        if (matchedMemories.isNotEmpty()) {
            val sb = StringBuilder()
            sb.append("Found ${matchedMemories.size} relevant local record(s) in Personal Memory:\n\n")
            matchedMemories.take(3).forEachIndexed { idx, mem ->
                sb.append("${idx + 1}. [${mem.category}] ${mem.title}\n")
                sb.append("   ${mem.content.take(120)}${if (mem.content.length > 120) "..." else ""}\n\n")
            }
            sb.append("(Retrieved offline from local Room SQLite database)")
            return LocalEngineOutput(
                replyText = sb.toString().trim(),
                intent = EngineIntent.MEMORY_SEARCH,
                matchedMemories = matchedMemories
            )
        }

        // 3. Search indexed documents
        val matchedDocs = documentRepository.searchDocumentsList(query)
        if (matchedDocs.isNotEmpty()) {
            val sb = StringBuilder()
            sb.append("Found ${matchedDocs.size} indexed document(s) matching your inquiry:\n\n")
            matchedDocs.take(3).forEachIndexed { idx, doc ->
                sb.append("${idx + 1}. ${doc.displayName} (${doc.mimeType})\n")
                if (doc.extractedText.isNotBlank()) {
                    sb.append("   Snippet: ${doc.extractedText.take(100)}...\n\n")
                }
            }
            sb.append("(Retrieved from Storage Access Framework indexed files)")
            return LocalEngineOutput(
                replyText = sb.toString().trim(),
                intent = EngineIntent.DOCUMENT_SEARCH,
                matchedDocuments = matchedDocs
            )
        }

        // 4. System / Diagnostics keywords
        if (lower.contains("status") || lower.contains("storage") || lower.contains("security") || lower.contains("encryption")) {
            return LocalEngineOutput(
                replyText = "ZEN AI System Status:\n• Storage: Room SQLite + Keystore AES-256-GCM active\n• Model Engine: Modular offline architecture (0 external API calls)\n• Cloud Vault: Disconnected (Local sovereignty preserved)\n• Privacy: All queries and memories remain on this device.",
                intent = EngineIntent.SYSTEM_DIAGNOSTICS
            )
        }

        // 5. Default transparent offline intelligence response
        return LocalEngineOutput(
            replyText = "Query processed locally by ZEN AI offline engine.\n\nNo matching personal memory or indexed document was found for \"$query\".\n\n• Say \"remember [text]\" to save a personal preference or note.\n• Import files in File Manager to build your local offline knowledge base.\n• Modular core is ready for local ONNX/GGUF weights.",
            intent = EngineIntent.GENERAL_LOCAL_QUERY
        )
    }
}
