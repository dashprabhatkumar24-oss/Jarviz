package com.example

import android.Manifest
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.JarvisViewModel
import com.example.ui.screens.CommandCenterScreen
import com.example.ui.screens.HudDashboardScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.SettingsPrivacyScreen
import com.example.ui.theme.MyApplicationTheme

enum class JarvisNavTab(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HUD(
        route = "hud",
        title = "HUD",
        selectedIcon = Icons.Filled.GraphicEq,
        unselectedIcon = Icons.Outlined.GraphicEq,
        testTag = "nav_tab_hud"
    ),
    TOOLS(
        route = "tools",
        title = "Tools",
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
        testTag = "nav_tab_tools"
    ),
    PERMISSIONS(
        route = "permissions",
        title = "Access",
        selectedIcon = Icons.Filled.Security,
        unselectedIcon = Icons.Outlined.Security,
        testTag = "nav_tab_permissions"
    ),
    SETTINGS(
        route = "settings",
        title = "Privacy & AI",
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        testTag = "nav_tab_settings"
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: JarvisViewModel = viewModel()
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = preferences.darkThemeEnabled) {
                JarvisMainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun JarvisMainApp(
    viewModel: JarvisViewModel
) {
    var currentTab by rememberSaveable { mutableStateOf(JarvisNavTab.HUD) }

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val orbState by viewModel.orbState.collectAsStateWithLifecycle()
    val audioLevelRms by viewModel.audioLevelRms.collectAsStateWithLifecycle()
    val livePartialTranscript by viewModel.livePartialTranscript.collectAsStateWithLifecycle()
    val currentTranscript by viewModel.currentTranscript.collectAsStateWithLifecycle()
    val latestAssistantMessage by viewModel.latestAssistantMessage.collectAsStateWithLifecycle()
    val latestActionDetail by viewModel.latestActionDetail.collectAsStateWithLifecycle()
    val telemetry by viewModel.deviceTelemetry.collectAsStateWithLifecycle()
    val commandLogs by viewModel.commandLogs.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val pendingPermissionPrompt by viewModel.pendingPermissionPrompt.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val permissionStatuses by viewModel.permissionStatuses.collectAsStateWithLifecycle()
    val isForegroundServiceRunning by viewModel.isForegroundServiceRunning.collectAsStateWithLifecycle()
    val isPausedForLowBattery by viewModel.isPausedForLowBattery.collectAsStateWithLifecycle()

    // Refresh permission and telemetry statuses whenever user returns from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Launcher for Microphone permission on-demand
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshSystemStatus()
        if (granted) {
            viewModel.onOrbOrMicTapped(onRequestMicPermission = {})
        }
    }

    // Launcher for single runtime permission from PermissionsScreen
    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshSystemStatus()
    }

    // Launcher for multiple runtime permissions required by a specific voice command
    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.refreshSystemStatus()
        if (results.values.all { it }) {
            viewModel.retryAfterPermissionGranted()
        }
    }

    // BackHandler on secondary tabs returns to HUD tab
    BackHandler(enabled = currentTab != JarvisNavTab.HUD) {
        currentTab = JarvisNavTab.HUD
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        JarvisNavTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title
                                    )
                                },
                                label = { Text(tab.title) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        JarvisNavTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title
                                    )
                                },
                                label = { Text(tab.title) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        JarvisNavTab.HUD -> {
                            HudDashboardScreen(
                                orbState = orbState,
                                audioLevelRms = audioLevelRms,
                                livePartialTranscript = livePartialTranscript,
                                currentTranscript = currentTranscript,
                                latestAssistantMessage = latestAssistantMessage,
                                latestActionDetail = latestActionDetail,
                                preferences = preferences,
                                telemetry = telemetry,
                                commandLogs = commandLogs,
                                reminders = reminders,
                                pendingConfirmation = pendingConfirmation,
                                pendingPermissionPrompt = pendingPermissionPrompt,
                                onOrbOrMicClick = {
                                    viewModel.onOrbOrMicTapped {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                onExecuteTextCommand = { cmd ->
                                    viewModel.processCommand(cmd)
                                },
                                onSelectLanguage = { langCode ->
                                    viewModel.updateLanguage(langCode)
                                },
                                onConfirmSensitiveAction = {
                                    viewModel.confirmPendingAction()
                                },
                                onCancelSensitiveAction = {
                                    viewModel.cancelPendingAction()
                                },
                                onGrantPendingPermissions = { perms, specialAction ->
                                    if (specialAction != null) {
                                        viewModel.permissionManager.openSpecialSettings(specialAction)
                                    } else if (perms.isNotEmpty()) {
                                        multiPermissionLauncher.launch(perms.toTypedArray())
                                    }
                                },
                                onDismissPermissionPrompt = {
                                    viewModel.dismissPermissionPrompt()
                                },
                                onToggleReminder = { id, completed ->
                                    viewModel.toggleReminderCompleted(id, completed)
                                },
                                onClearHistory = {
                                    viewModel.clearHistory()
                                }
                            )
                        }

                        JarvisNavTab.TOOLS -> {
                            CommandCenterScreen(
                                installedApps = installedApps,
                                onExecuteCommand = { cmd ->
                                    currentTab = JarvisNavTab.HUD
                                    viewModel.processCommand(cmd)
                                }
                            )
                        }

                        JarvisNavTab.PERMISSIONS -> {
                            PermissionsScreen(
                                permissions = permissionStatuses,
                                onRequestRuntimePermission = { perm ->
                                    singlePermissionLauncher.launch(perm)
                                },
                                onOpenSpecialSettings = { action ->
                                    viewModel.permissionManager.openSpecialSettings(action)
                                },
                                onOpenAppSystemSettings = {
                                    viewModel.permissionManager.openAppSystemSettings()
                                },
                                onRefreshPermissions = {
                                    viewModel.refreshSystemStatus()
                                }
                            )
                        }

                        JarvisNavTab.SETTINGS -> {
                            SettingsPrivacyScreen(
                                preferences = preferences,
                                isApiKeyConfigured = viewModel.aiEngine.isApiKeyConfigured(),
                                isForegroundServiceRunning = isForegroundServiceRunning,
                                isPausedForLowBattery = isPausedForLowBattery,
                                onUpdateAssistantName = { viewModel.updateAssistantName(it) },
                                onUpdateWakeWord = { viewModel.updateWakeWord(it) },
                                onToggleWakeWord = { viewModel.setWakeWordEnabled(it) },
                                onUpdateLanguage = { viewModel.updateLanguage(it) },
                                onUpdatePersonality = { viewModel.updatePersonality(it) },
                                onUpdateSpeechRate = { viewModel.updateSpeechRate(it) },
                                onUpdateVoicePitch = { viewModel.updateVoicePitch(it) },
                                onToggleTts = { viewModel.setTtsEnabled(it) },
                                onToggleCloudAi = { viewModel.setCloudAiEnabled(it) },
                                onToggleConfirmSensitive = { viewModel.setConfirmSensitiveActions(it) },
                                onToggleForegroundService = { enable ->
                                    if (enable && !viewModel.permissionManager.isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        viewModel.toggleForegroundVoiceGuard(enable)
                                    }
                                },
                                onToggleAutoPauseBattery = { viewModel.setAutoPauseLowBattery(it) },
                                onToggleDarkTheme = { viewModel.setDarkThemeEnabled(it) },
                                onOpenNotificationListenerSettings = {
                                    viewModel.permissionManager.openSpecialSettings(
                                        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                                    )
                                },
                                onClearHistory = { viewModel.clearHistory() },
                                onWipeAllData = { viewModel.wipeAllData() }
                            )
                        }
                    }
                }
            }
        }
    }
}
