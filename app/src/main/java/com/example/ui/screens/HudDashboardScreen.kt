package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.android.DeviceTelemetry
import com.example.commands.CommandExecutionResult
import com.example.database.CommandLogEntity
import com.example.database.ReminderEntity
import com.example.settings.JarvisPreferences
import com.example.settings.SUPPORTED_LANGUAGES
import com.example.ui.PendingPermissionPrompt
import com.example.ui.components.JarvisHudOrb
import com.example.ui.theme.JarvisAmberWarning
import com.example.ui.theme.JarvisCrimsonAlert
import com.example.ui.theme.JarvisCyanPrimary
import com.example.ui.theme.JarvisEmeraldOnline
import com.example.voice.VoiceOrbState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class QuickCommandItem(
    val label: String,
    val command: String,
    val icon: ImageVector
)

val QUICK_COMMANDS = listOf(
    QuickCommandItem("Set 7 AM Alarm", "Hey Jarvis, set an alarm for 7:00 AM", Icons.Default.AccessAlarm),
    QuickCommandItem("Battery Status", "What's my battery percentage?", Icons.Default.BatteryChargingFull),
    QuickCommandItem("Open YouTube", "Open YouTube", Icons.Default.MusicNote),
    QuickCommandItem("Call Mom", "Hey Jarvis, call Mom", Icons.Default.Call),
    QuickCommandItem("WhatsApp Follow-up", "Actually, send her a WhatsApp message saying I'll be home in 20 minutes", Icons.AutoMirrored.Filled.Send),
    QuickCommandItem("Hinglish Alarm", "Jarvis, kal subah 7 baje alarm laga do", Icons.Default.Language),
    QuickCommandItem("Switch to Odia", "Speak to me in Odia", Icons.Default.Language),
    QuickCommandItem("Wi-Fi Settings", "Take me to the Wi-Fi settings", Icons.Default.Settings),
    QuickCommandItem("Volume Down", "Turn the volume down", Icons.AutoMirrored.Filled.VolumeUp),
    QuickCommandItem("Nearest Petrol", "Navigate to the nearest petrol station", Icons.Default.Navigation),
    QuickCommandItem("Read Notifications", "Read my notifications", Icons.Default.NotificationsActive),
    QuickCommandItem("Weather Tomorrow", "Jarvis, what's the weather tomorrow?", Icons.Default.Wifi)
)

@Composable
fun HudDashboardScreen(
    orbState: VoiceOrbState,
    audioLevelRms: Float,
    livePartialTranscript: String,
    currentTranscript: String,
    latestAssistantMessage: String,
    latestActionDetail: String?,
    preferences: JarvisPreferences,
    telemetry: DeviceTelemetry,
    commandLogs: List<CommandLogEntity>,
    reminders: List<ReminderEntity>,
    pendingConfirmation: CommandExecutionResult.RequiresConfirmation?,
    pendingPermissionPrompt: PendingPermissionPrompt?,
    onOrbOrMicClick: () -> Unit,
    onExecuteTextCommand: (String) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onConfirmSensitiveAction: () -> Unit,
    onCancelSensitiveAction: () -> Unit,
    onGrantPendingPermissions: (List<String>, String?) -> Unit,
    onDismissPermissionPrompt: () -> Unit,
    onToggleReminder: (Long, Boolean) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var commandText by rememberSaveable { mutableStateOf("") }
    var showLanguageMenu by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val activeLanguageName = remember(preferences.selectedLanguageCode, preferences.autoDetectLanguage) {
        if (preferences.autoDetectLanguage) {
            "Auto (${preferences.selectedLanguageCode})"
        } else {
            SUPPORTED_LANGUAGES.find { it.code == preferences.selectedLanguageCode }?.name
                ?: preferences.selectedLanguageCode
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("hud_dashboard_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Telemetry & Language Selector Header Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hud_banner),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.22f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = preferences.assistantName.uppercase(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (telemetry.isNetworkOnline && preferences.cloudAiEnabled) {
                                        "ONLINE NEURAL CORE • ${telemetry.networkType.uppercase()}"
                                    } else {
                                        "OFFLINE LOCAL CORE • READY"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (telemetry.isNetworkOnline) JarvisEmeraldOnline else JarvisAmberWarning
                                )
                            }

                            Box {
                                FilterChip(
                                    selected = true,
                                    onClick = { showLanguageMenu = true },
                                    label = {
                                        Text(
                                            text = activeLanguageName,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = "Select Assistant Language",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("language_selector_chip")
                                )

                                DropdownMenu(
                                    expanded = showLanguageMenu,
                                    onDismissRequest = { showLanguageMenu = false }
                                ) {
                                    SUPPORTED_LANGUAGES.forEach { lang ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = "${lang.name} (${lang.nativeName})",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = lang.sampleCommand,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            },
                                            onClick = {
                                                onSelectLanguage(lang.code)
                                                showLanguageMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Live Telemetry Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TelemetryBadge(
                                icon = Icons.Default.BatteryChargingFull,
                                label = "PWR ${telemetry.batteryPercentage}%"
                            )
                            TelemetryBadge(
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                label = "VOL ${telemetry.mediaVolumePercent}%"
                            )
                            TelemetryBadge(
                                icon = Icons.Default.Memory,
                                label = "RAM ${telemetry.availableRamMb}MB"
                            )
                        }
                    }
                }
            }
        }

        // 2. Central Animated JARVIS Orb + Live Voice Transcription & AI Response
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(vertical = 20.dp, horizontal = 16.dp)
                ) {
                    JarvisHudOrb(
                        orbState = orbState,
                        audioLevelRms = audioLevelRms,
                        assistantName = preferences.assistantName,
                        onClick = onOrbOrMicClick
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val displayedTranscript = if (orbState == VoiceOrbState.LISTENING && livePartialTranscript.isNotBlank()) {
                        livePartialTranscript
                    } else {
                        currentTranscript
                    }

                    if (displayedTranscript.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("user_transcript_box")
                        ) {
                            Text(
                                text = "\"$displayedTranscript\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // JARVIS Spoken Response Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_response_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${preferences.assistantName} RESPONSE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (latestActionDetail != null) {
                                    Text(
                                        text = latestActionDetail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = latestAssistantMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("ai_response_text")
                            )
                        }
                    }
                }
            }
        }

        // 3. Sensitive Action Security Confirmation Banner (if awaiting confirmation)
        item {
            AnimatedVisibility(visible = pendingConfirmation != null) {
                pendingConfirmation?.let { conf ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = JarvisCrimsonAlert.copy(alpha = 0.14f)
                        ),
                        border = BorderStroke(1.5.dp, JarvisCrimsonAlert),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirmation_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Security Confirmation",
                                    tint = JarvisCrimsonAlert
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SECURITY AUTHORIZATION REQUIRED",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = JarvisCrimsonAlert
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = conf.reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onCancelSensitiveAction,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("cancel_action_button")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = onConfirmSensitiveAction,
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCrimsonAlert),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("confirm_action_button")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Authorize")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. On-Demand Permission Request Banner (if an action needed a missing permission)
        item {
            AnimatedVisibility(visible = pendingPermissionPrompt != null) {
                pendingPermissionPrompt?.let { prompt ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = JarvisAmberWarning.copy(alpha = 0.14f)
                        ),
                        border = BorderStroke(1.5.dp, JarvisAmberWarning),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("permission_prompt_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = "Permission Needed",
                                    tint = JarvisAmberWarning
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ANDROID PERMISSION REQUIRED",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = JarvisAmberWarning
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = prompt.explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismissPermissionPrompt,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Dismiss")
                                }
                                Button(
                                    onClick = {
                                        onGrantPendingPermissions(
                                            prompt.missingPermissions,
                                            prompt.specialSettingsAction
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("grant_pending_permission_button")
                                ) {
                                    Text("Grant Access")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Command Input Console (Voice + Natural Language Text Input)
        item {
            OutlinedTextField(
                value = commandText,
                onValueChange = { commandText = it },
                placeholder = {
                    Text(
                        text = "Say \"${preferences.wakeWord}\" or type command...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    IconButton(
                        onClick = onOrbOrMicClick,
                        modifier = Modifier.testTag("console_mic_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Speak Command",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (commandText.isNotBlank()) {
                                onExecuteTextCommand(commandText)
                                commandText = ""
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.testTag("send_command_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Execute Command",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (commandText.isNotBlank()) {
                            onExecuteTextCommand(commandText)
                            commandText = ""
                            keyboardController?.hide()
                        }
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("command_input_field")
            )
        }

        // 6. Quick Action Chips (Multilingual & Core Phone Controls)
        item {
            Column {
                Text(
                    text = "QUICK VOICE & PHONE COMMANDS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QUICK_COMMANDS.forEach { qc ->
                        AssistChip(
                            onClick = { onExecuteTextCommand(qc.command) },
                            label = { Text(qc.label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = qc.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier.testTag("quick_chip_${qc.label.lowercase().replace(" ", "_")}")
                        )
                    }
                }
            }
        }

        // 7. Active Reminders Section (if any exist)
        if (reminders.isNotEmpty()) {
            item {
                Text(
                    text = "ACTIVE REMINDERS (${reminders.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(reminders.take(4), key = { "rem_${it.id}" }) { reminder ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleReminder(reminder.id, !reminder.isCompleted) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Complete Reminder",
                            tint = if (reminder.isCompleted) JarvisEmeraldOnline else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reminder.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${reminder.scheduledDate} • ${reminder.scheduledTime}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 8. Contextual Conversation & Command Execution History
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "COMMAND TELEMETRY LOG (${commandLogs.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (commandLogs.isNotEmpty()) {
                    IconButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Command History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (commandLogs.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "No commands logged yet.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the glowing HUD orb or any quick chip above to test voice actions, follow-up context, or multilingual commands.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(commandLogs.take(15), key = { it.id }) { log ->
                CommandLogCard(log = log, onReplayCommand = onExecuteTextCommand)
            }
        }
    }
}

@Composable
private fun TelemetryBadge(
    icon: ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = JarvisCyanPrimary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CommandLogCard(
    log: CommandLogEntity,
    onReplayCommand: (String) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onReplayCommand(log.userTranscript) }
            .testTag("command_log_item_${log.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${log.actionCategory} • ${log.languageCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${if (log.wasOffline) "OFFLINE" else "AI"} • ${timeFormat.format(Date(log.timestamp))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "You: \"${log.userTranscript}\"",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "JARVIS: ${log.assistantResponse}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
