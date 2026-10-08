package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.settings.JarvisPreferences
import com.example.settings.PersonalityPreset
import com.example.settings.SUPPORTED_LANGUAGES
import com.example.ui.theme.JarvisAmberWarning
import com.example.ui.theme.JarvisCrimsonAlert
import com.example.ui.theme.JarvisEmeraldOnline
import java.util.Locale

@Composable
fun SettingsPrivacyScreen(
    preferences: JarvisPreferences,
    isApiKeyConfigured: Boolean,
    isForegroundServiceRunning: Boolean,
    isPausedForLowBattery: Boolean,
    onUpdateAssistantName: (String) -> Unit,
    onUpdateWakeWord: (String) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onUpdateLanguage: (String) -> Unit,
    onUpdatePersonality: (PersonalityPreset) -> Unit,
    onUpdateSpeechRate: (Float) -> Unit,
    onUpdateVoicePitch: (Float) -> Unit,
    onToggleTts: (Boolean) -> Unit,
    onToggleCloudAi: (Boolean) -> Unit,
    onToggleConfirmSensitive: (Boolean) -> Unit,
    onToggleForegroundService: (Boolean) -> Unit,
    onToggleAutoPauseBattery: (Boolean) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onClearHistory: () -> Unit,
    onWipeAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nameInput by remember(preferences.assistantName) { mutableStateOf(preferences.assistantName) }
    var wakeWordInput by remember(preferences.wakeWord) { mutableStateOf(preferences.wakeWord) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_privacy_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "AI CONFIGURATION & PRIVACY",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Customize assistant identity, multilingual voice synthesis, background wake-word guard, and zero-trust privacy controls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Assistant Persona & Wake-Word Identity
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "IDENTITY & PERSONALITY MATRIX",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            onUpdateAssistantName(it)
                        },
                        label = { Text("Assistant Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("assistant_name_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = wakeWordInput,
                        onValueChange = {
                            wakeWordInput = it
                            onUpdateWakeWord(it)
                        },
                        label = { Text("Wake-Word Phrase (e.g., Hey Jarvis)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wake_word_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Response Personality Style",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    PersonalityPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = preferences.personalityPreset == preset,
                            onClick = { onUpdatePersonality(preset) },
                            label = { Text(preset.displayName) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .testTag("personality_chip_${preset.name}")
                        )
                    }
                }
            }
        }

        // 2. Voice Synthesis (TTS) & Multilingual Engine
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VOICE SYNTHESIS & MULTILINGUAL TTS",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Spoken Voice Responses (TTS)",
                        subtitle = "Speak JARVIS responses aloud with immediate interruption support",
                        checked = preferences.ttsEnabled,
                        onCheckedChange = onToggleTts,
                        testTag = "toggle_tts_switch"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Speech Rate: ${String.format(Locale.US, "%.2fx", preferences.speechRate)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = preferences.speechRate,
                        onValueChange = onUpdateSpeechRate,
                        valueRange = 0.6f..1.8f,
                        modifier = Modifier.testTag("speech_rate_slider")
                    )

                    Text(
                        text = "Voice Pitch: ${String.format(Locale.US, "%.2f", preferences.voicePitch)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = preferences.voicePitch,
                        onValueChange = onUpdateVoicePitch,
                        valueRange = 0.6f..1.5f,
                        modifier = Modifier.testTag("voice_pitch_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Supported Languages (${SUPPORTED_LANGUAGES.size - 1} Languages + Auto-Detect)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = SUPPORTED_LANGUAGES.drop(1).joinToString(" • ") { "${it.name} (${it.nativeName})" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. Privacy, Security & Background Operation Controls
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PRIVACY & SECURITY CONTROLS",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Wake-Word Recognition ('${preferences.wakeWord}')",
                        subtitle = "Allow voice activation when microphone is active",
                        checked = preferences.wakeWordEnabled,
                        onCheckedChange = onToggleWakeWord,
                        testTag = "toggle_wake_word_switch"
                    )

                    SettingToggleRow(
                        title = "Online Cloud AI (Gemini 3.5 Flash)",
                        subtitle = if (isApiKeyConfigured) {
                            "GEMINI_API_KEY active via Secrets panel • Disable to enforce 100% offline processing"
                        } else {
                            "Configure GEMINI_API_KEY in the AI Studio Secrets panel for online LLM reasoning"
                        },
                        checked = preferences.cloudAiEnabled,
                        onCheckedChange = onToggleCloudAi,
                        testTag = "toggle_cloud_ai_switch"
                    )

                    SettingToggleRow(
                        title = "Confirm Sensitive Phone Actions",
                        subtitle = "Always ask for explicit confirmation before placing calls or sending SMS/WhatsApp messages",
                        checked = preferences.confirmSensitiveActions,
                        onCheckedChange = onToggleConfirmSensitive,
                        testTag = "toggle_confirm_sensitive_switch"
                    )

                    SettingToggleRow(
                        title = "Foreground Voice Guard Service",
                        subtitle = if (isForegroundServiceRunning) {
                            "Active with persistent privacy notification"
                        } else {
                            "Keep JARVIS accessible in the status bar with a clear microphone indicator"
                        },
                        checked = preferences.backgroundServiceEnabled,
                        onCheckedChange = onToggleForegroundService,
                        testTag = "toggle_background_service_switch"
                    )

                    SettingToggleRow(
                        title = "Battery Saver Auto-Pause (<15%)",
                        subtitle = if (isPausedForLowBattery) {
                            "Currently paused due to low battery"
                        } else {
                            "Automatically pause continuous voice listening when battery is critically low"
                        },
                        checked = preferences.autoPauseLowBattery,
                        onCheckedChange = onToggleAutoPauseBattery,
                        testTag = "toggle_battery_pause_switch"
                    )

                    SettingToggleRow(
                        title = "Dark Futuristic HUD Theme",
                        subtitle = "Switch between deep-space OLED HUD and high-contrast daylight mode",
                        checked = preferences.darkThemeEnabled,
                        onCheckedChange = onToggleDarkTheme,
                        testTag = "toggle_dark_theme_switch"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onOpenNotificationListenerSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manage_notification_access_button")
                    ) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage / Disable Notification Access")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClearHistory,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("privacy_clear_history_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Logs")
                        }

                        Button(
                            onClick = onWipeAllData,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCrimsonAlert),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("privacy_wipe_all_button")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Wipe All Data")
                        }
                    }
                }
            }
        }

        // 4. Prototype API Key Security Notice
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = JarvisAmberWarning.copy(alpha = 0.12f)
                ),
                border = BorderStroke(1.dp, JarvisAmberWarning.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = JarvisAmberWarning
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(id = R.string.security_prototype_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
