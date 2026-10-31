package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class ZenScreen(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val testTag: String
) {
    DASHBOARD(
        title = "Dashboard",
        subtitle = "System Overview",
        icon = Icons.Default.Assistant,
        testTag = "screen_dashboard"
    ),
    AI_ASSISTANT(
        title = "AI Assistant",
        subtitle = "Offline Local Intelligence",
        icon = Icons.Default.Assistant,
        testTag = "section_ai_assistant"
    ),
    PERSONAL_MEMORY(
        title = "Personal Memory",
        subtitle = "Encrypted Notes & Preferences",
        icon = Icons.Default.Memory,
        testTag = "section_personal_memory"
    ),
    CONVERSATIONS(
        title = "Conversations",
        subtitle = "Local Chat History",
        icon = Icons.Default.Forum,
        testTag = "section_conversations"
    ),
    FILE_MANAGER(
        title = "File Manager",
        subtitle = "SAF Document Storage",
        icon = Icons.Default.Folder,
        testTag = "section_file_manager"
    ),
    KNOWLEDGE_LIBRARY(
        title = "Knowledge Library",
        subtitle = "Indexed Snippets & Insights",
        icon = Icons.Default.LocalLibrary,
        testTag = "section_knowledge_library"
    ),
    PHONE_ACTIONS(
        title = "Phone Actions",
        subtitle = "Hardware & System Telemetry",
        icon = Icons.Default.PhoneAndroid,
        testTag = "section_phone_actions"
    ),
    GOOGLE_DRIVE_VAULT(
        title = "Google Drive Vault",
        subtitle = "Cloud Sync & Conflict Policy",
        icon = Icons.Default.CloudSync,
        testTag = "section_google_drive_vault"
    ),
    BACKUP_RECOVERY(
        title = "Backup & Recovery",
        subtitle = "AES-256-GCM & Checksums",
        icon = Icons.Default.Backup,
        testTag = "section_backup_recovery"
    ),
    SETTINGS(
        title = "Settings",
        subtitle = "Keystore & App Security",
        icon = Icons.Default.Settings,
        testTag = "section_settings"
    )
}
