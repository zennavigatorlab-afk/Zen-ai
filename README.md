# ZEN AI – Personal Intelligence System

ZEN AI is a private, local-first personal intelligence system engineered with zero paid APIs, zero cloud tracking, and hardware-guarded encryption.

It exists both as a **Progressive Web App (PWA)** for instant cross-platform desktop and mobile use, and as a **native Android Application (Kotlin & Jetpack Compose)**.

---

## Key Features

1. **AI Assistant (Offline Local Intelligence)**:
   - Evaluates intents, notes capture, memory recall, and document lookup locally on-device.
   - Zero external calls to Gemini, OpenAI, or paid AI APIs.
2. **Personal Memory**:
   - Save personal notes, preferences, and knowledge.
   - Categorization (*Preferences, Work, Personal, Health, Ideas, General*).
   - Real-time search.
   - AES-256-GCM encryption & signed JSON export/import.
3. **Conversations**:
   - Local chat sessions stored in SQLite / IndexedDB.
4. **File Manager**:
   - Zero broad permissions.
   - Indexed documents, real byte sizes, MIME types, text previews, rename and delete controls.
5. **Knowledge Library**:
   - Browse and query extracted document snippets offline.
6. **Phone & Web Actions**:
   - Real battery level, storage estimate, device specs, network state, and system share sheet.
7. **Google Drive Vault Foundation**:
   - Dedicated `ZEN_AI_VAULT` folder architecture.
   - Narrow `drive.file` scope.
   - Honest "Not connected" status until OAuth 2.0 configuration.
   - Conflict detection and sync queue logic.
8. **Backup & Recovery**:
   - Atomic encrypted `.zenvault` backups.
   - SHA-256 integrity verification tag.
   - Restore with tamper checks.
9. **Settings & Security**:
   - Web Crypto / Android Keystore AES-256-GCM status.
   - 12-word secure recovery key generator.
   - Salted PBKDF2 passphrase hashing.
   - Dark & Light mode toggle.

---

## Quick Start (PWA)

```bash
# Run local dev server
npm start
# Visit http://localhost:3000
```

Refer to `PWA_DEPLOYMENT_GUIDE.md` for full deployment and installation steps.
