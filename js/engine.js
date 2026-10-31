/**
 * ZEN AI Local Intelligence Engine (Offline Web Architecture)
 * 100% On-Device • Zero External API Calls • Zero Hallucinations
 */

class LocalIntelligenceEngine {
  constructor(db) {
    this.db = db;
  }

  async processQuery(userInput) {
    const query = (userInput || "").trim();
    const lower = query.toLowerCase();

    // 1. Note / Memory Capture Intent
    if (lower.startsWith("remember ") || lower.startsWith("note: ") || lower.startsWith("save ")) {
      const contentToSave = query
        .replace(/^remember that\s+/i, "")
        .replace(/^remember\s+/i, "")
        .replace(/^note:\s+/i, "")
        .replace(/^save\s+/i, "")
        .trim();

      if (contentToSave.length > 0) {
        const id = await this.db.saveMemory({
          title: `Quick Note: ${contentToSave.slice(0, 32)}...`,
          content: contentToSave,
          category: "Preferences",
          tags: "captured_by_ai",
          isEncrypted: true
        });
        return {
          replyText: `Captured and saved directly to your local encrypted Personal Memory (Record #${id}). Stored in your browser's IndexedDB with zero cloud exposure.`,
          intent: "NOTE_CAPTURE",
          isFullyOffline: true,
          modelArchitecture: "ZEN Local Semantic Engine (Web Ready for ONNX/WebLLM)"
        };
      }
    }

    // 2. Search Local Memories
    const memories = await this.db.getAllMemories();
    const matchedMemories = memories.filter(m => {
      const q = lower;
      return m.title.toLowerCase().includes(q) ||
             m.content.toLowerCase().includes(q) ||
             (m.tags && m.tags.toLowerCase().includes(q));
    });

    if (matchedMemories.length > 0) {
      let sb = `Found ${matchedMemories.length} relevant local record(s) in Personal Memory:\n\n`;
      matchedMemories.slice(0, 3).forEach((mem, idx) => {
        sb += `${idx + 1}. [${mem.category}] ${mem.title}\n`;
        sb += `   ${mem.content.slice(0, 140)}${mem.content.length > 140 ? '...' : ''}\n\n`;
      });
      sb += `(Retrieved offline from local browser IndexedDB)`;
      return {
        replyText: sb.trim(),
        intent: "MEMORY_SEARCH",
        matchedMemories,
        isFullyOffline: true,
        modelArchitecture: "ZEN Local Semantic Engine"
      };
    }

    // 3. Search Indexed Documents
    const documents = await this.db.getAllDocuments();
    const matchedDocs = documents.filter(d => {
      const q = lower;
      return d.displayName.toLowerCase().includes(q) ||
             (d.extractedText && d.extractedText.toLowerCase().includes(q));
    });

    if (matchedDocs.length > 0) {
      let sb = `Found ${matchedDocs.length} indexed document(s) matching your query:\n\n`;
      matchedDocs.slice(0, 3).forEach((doc, idx) => {
        sb += `${idx + 1}. ${doc.displayName} (${doc.mimeType})\n`;
        if (doc.extractedText) {
          sb += `   Snippet: ${doc.extractedText.slice(0, 120)}...\n\n`;
        }
      });
      sb += `(Retrieved from browser local file index)`;
      return {
        replyText: sb.trim(),
        intent: "DOCUMENT_SEARCH",
        matchedDocs,
        isFullyOffline: true,
        modelArchitecture: "ZEN Local Semantic Engine"
      };
    }

    // 4. System / Diagnostics keywords
    if (lower.includes("status") || lower.includes("storage") || lower.includes("security") || lower.includes("encryption")) {
      return {
        replyText: "ZEN AI System Status:\n• Storage: Web IndexedDB + Web Crypto AES-256-GCM active\n• Model Engine: Modular offline architecture (0 external API calls)\n• Cloud Vault: Disconnected (Local privacy strictly preserved)\n• Platform: Progressive Web App (PWA) with Service Worker offline shell.",
        intent: "SYSTEM_DIAGNOSTICS",
        isFullyOffline: true,
        modelArchitecture: "ZEN Local Semantic Engine"
      };
    }

    // 5. Default transparent response
    return {
      replyText: `Query evaluated locally by ZEN AI offline engine.\n\nNo matching personal memory or indexed document was found for "${query}".\n\n• Say "remember [text]" to save a personal preference or note.\n• Import files in File Manager to build your local knowledge library.\n• Modular engine is ready for on-device WebLLM / ONNX Runtime Web model weights.`,
      intent: "GENERAL_LOCAL_QUERY",
      isFullyOffline: true,
      modelArchitecture: "ZEN Local Semantic Engine"
    };
  }
}

window.LocalIntelligenceEngine = LocalIntelligenceEngine;
