package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.assistant.AssistantScreen
import com.example.ui.assistant.AssistantViewModel
import com.example.ui.assistant.ConversationsScreen
import com.example.ui.backup.BackupRecoveryScreen
import com.example.ui.backup.BackupViewModel
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.files.FileManagerScreen
import com.example.ui.files.FileManagerViewModel
import com.example.ui.knowledge.KnowledgeLibraryScreen
import com.example.ui.memory.MemoryScreen
import com.example.ui.memory.MemoryViewModel
import com.example.ui.navigation.ZenScreen
import com.example.ui.phoneactions.PhoneActionsScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.ZenAiTheme
import com.example.ui.vault.GoogleDriveVaultScreen

class MainActivity : ComponentActivity() {

    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val memoryViewModel: MemoryViewModel by viewModels()
    private val assistantViewModel: AssistantViewModel by viewModels()
    private val fileManagerViewModel: FileManagerViewModel by viewModels()
    private val backupViewModel: BackupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZenAiTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    ZenAppContent(
                        dashboardViewModel = dashboardViewModel,
                        memoryViewModel = memoryViewModel,
                        assistantViewModel = assistantViewModel,
                        fileManagerViewModel = fileManagerViewModel,
                        backupViewModel = backupViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun ZenAppContent(
    dashboardViewModel: DashboardViewModel,
    memoryViewModel: MemoryViewModel,
    assistantViewModel: AssistantViewModel,
    fileManagerViewModel: FileManagerViewModel,
    backupViewModel: BackupViewModel
) {
    var currentScreen by remember { mutableStateOf(ZenScreen.DASHBOARD) }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            ZenScreen.DASHBOARD -> DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigate = { currentScreen = it }
            )
            ZenScreen.AI_ASSISTANT -> AssistantScreen(
                viewModel = assistantViewModel,
                onNavigateBack = { currentScreen = ZenScreen.DASHBOARD }
            )
            ZenScreen.PERSONAL_MEMORY -> MemoryScreen(
                viewModel = memoryViewModel,
                onNavigateBack = { currentScreen = ZenScreen.DASHBOARD }
            )
            ZenScreen.CONVERSATIONS -> ConversationsScreen(
                viewModel = assistantViewModel,
                onNavigateBack = { currentScreen = ZenScreen.DASHBOARD },
                onOpenConversation = { currentScreen = ZenScreen.AI_ASSISTANT }
            )
            ZenScreen.FILE_MANAGER -> FileManagerScreen(
                viewModel = fileManagerViewModel,
                onNavigateBack = { currentScreen = ZenScreen.DASHBOARD }
            )
            ZenScreen.KNOWLEDGE_LIBRARY -> KnowledgeLibraryScreen(
                viewModel = fileManagerViewModel,
                onNavigateBack = { currentScreen = ZenScreen.DASHBOARD }
            )
            ZenScreen.PHONE_ACTIONS -> PhoneActionsScreen(
                onNavigateBack = { currentScreen = ZenScreen.DASHBOARD }
            )
            ZenScreen.GOOGLE_DRIVE_VAULT -> GoogleDriveVaultScreen(
                onNavigateBack = { currentScreen = ZenScreen.DASHBOARD }
            )
            ZenScreen.BACKUP_RECOVERY -> BackupRecoveryScreen(
                viewModel = backupViewModel,
                onNavigateBack = { currentScreen = ZenScreen.DASHBOARD }
            )
            ZenScreen.SETTINGS -> SettingsScreen(
                onNavigateBack = { currentScreen = ZenScreen.DASHBOARD }
            )
        }
    }
}
