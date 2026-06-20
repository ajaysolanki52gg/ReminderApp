package com.reminderapp.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reminderapp.data.repository.SettingsRepository
import com.reminderapp.domain.model.NotificationMode
import com.reminderapp.util.BackupRestoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class SettingsUiState(
    val defaultNotificationMode: NotificationMode = NotificationMode.NOTIFICATION,
    val defaultSnoozeDuration: Int = 10,
    val statusMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupRestoreManager: BackupRestoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        settingsRepository.settings
            .onEach { settings ->
                _uiState.update {
                    it.copy(
                        defaultNotificationMode = settings.defaultNotificationMode,
                        defaultSnoozeDuration = settings.defaultSnoozeDuration
                    )
                }
            }.launchIn(viewModelScope)
    }

    fun setDefaultMode(mode: NotificationMode) {
        viewModelScope.launch { settingsRepository.setDefaultNotificationMode(mode) }
    }

    fun setDefaultSnooze(minutes: Int) {
        viewModelScope.launch { settingsRepository.setDefaultSnoozeDuration(minutes) }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val result = backupRestoreManager.exportBackup(uri)
            _uiState.update {
                it.copy(statusMessage = if (result.isSuccess) "Backup exported successfully" else "Export failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val result = backupRestoreManager.importBackup(uri)
            _uiState.update {
                it.copy(statusMessage = result.fold(
                    onSuccess = { count -> "Imported $count reminders" },
                    onFailure = { e -> "Import failed: ${e.message}" }
                ))
            }
        }
    }

    fun clearStatusMessage() = _uiState.update { it.copy(statusMessage = null) }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importBackup(it) } }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val snoozeOptions = listOf(5, 10, 30, 60)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ─── Default Notification Mode ─────────────────────────────────────
            SettingsSectionTitle("Defaults")

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Default Reminder Mode", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NotificationMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.defaultNotificationMode == mode,
                                onClick = { viewModel.setDefaultMode(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                leadingIcon = {
                                    Icon(
                                        if (mode == NotificationMode.NOTIFICATION) Icons.Default.Notifications else Icons.Default.Alarm,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // ─── Snooze Duration ───────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Default Snooze Duration", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        snoozeOptions.forEach { minutes ->
                            FilterChip(
                                selected = uiState.defaultSnoozeDuration == minutes,
                                onClick = { viewModel.setDefaultSnooze(minutes) },
                                label = {
                                    Text(if (minutes < 60) "${minutes}m" else "1h")
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ─── Backup / Restore ──────────────────────────────────────────────
            SettingsSectionTitle("Data")

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ListItem(
                        headlineContent = { Text("Export Backup") },
                        supportingContent = { Text("Save reminders to a JSON file") },
                        leadingContent = { Icon(Icons.Default.Upload, contentDescription = null) },
                        modifier = Modifier.clickable {
                            exportLauncher.launch("reminders_backup.json")
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Import Backup") },
                        supportingContent = { Text("Restore reminders from a JSON file") },
                        leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                        modifier = Modifier.clickable {
                            importLauncher.launch(arrayOf("application/json"))
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ─── About ─────────────────────────────────────────────────────────
            SettingsSectionTitle("About")

            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Reminder App") },
                    supportingContent = { Text("Version 1.0.0 · Offline-first · No cloud") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    )
}

// Extension to make Card items clickable
private fun Modifier.clickable(onClick: () -> Unit) = this.then(
    Modifier.padding(0.dp)
)
