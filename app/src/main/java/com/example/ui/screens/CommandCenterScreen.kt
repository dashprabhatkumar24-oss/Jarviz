package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.android.InstalledAppInfo
import com.example.commands.ActionCategory
import com.example.ui.theme.JarvisAmberWarning
import com.example.ui.theme.JarvisEmeraldOnline

data class SampleToolPrompt(
    val category: ActionCategory,
    val sampleVoiceCommand: String
)

val SAMPLE_TOOL_PROMPTS = listOf(
    SampleToolPrompt(ActionCategory.CALL_CONTACT, "Hey Jarvis, call John"),
    SampleToolPrompt(ActionCategory.SEND_SMS, "Send Mom an SMS saying I'll be late"),
    SampleToolPrompt(ActionCategory.SEND_MESSAGE, "Send a WhatsApp message to Mom saying I'll be home in 20 minutes"),
    SampleToolPrompt(ActionCategory.OPEN_APP, "Open Camera"),
    SampleToolPrompt(ActionCategory.SEARCH_WEB, "Open YouTube and search for latest technology news"),
    SampleToolPrompt(ActionCategory.CREATE_ALARM, "Set an alarm for 6:30 tomorrow morning"),
    SampleToolPrompt(ActionCategory.CREATE_TIMER, "Set a timer for 5 minutes"),
    SampleToolPrompt(ActionCategory.CREATE_REMINDER, "Remind me to call Rahul tomorrow at 10 AM"),
    SampleToolPrompt(ActionCategory.CREATECALENDAREVENT, "What's on my calendar today?"),
    SampleToolPrompt(ActionCategory.CONTROL_VOLUME, "Turn the volume down"),
    SampleToolPrompt(ActionCategory.CONTROL_BRIGHTNESS, "Set brightness to 80 percent"),
    SampleToolPrompt(ActionCategory.OPEN_SETTINGS, "Open Bluetooth settings"),
    SampleToolPrompt(ActionCategory.PLAY_MEDIA, "Play music"),
    SampleToolPrompt(ActionCategory.PAUSE_MEDIA, "Pause the music"),
    SampleToolPrompt(ActionCategory.NAVIGATE, "Navigate to the nearest petrol station"),
    SampleToolPrompt(ActionCategory.READ_NOTIFICATION, "Read my notifications"),
    SampleToolPrompt(ActionCategory.DEVICE_INFORMATION, "What's my battery percentage?"),
    SampleToolPrompt(ActionCategory.WEATHER, "What's the weather tomorrow?"),
    SampleToolPrompt(ActionCategory.SWITCH_LANGUAGE, "Speak to me in Odia"),
    SampleToolPrompt(ActionCategory.GENERALAIQUERY, "Translate 'Good morning, how are you?' into Hindi")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommandCenterScreen(
    installedApps: List<InstalledAppInfo>,
    onExecuteCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("command_center_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "AI TOOL & ACTION REGISTRY",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "JARVIS never executes arbitrary code. Every spoken or typed intent is validated against these 20 structured Android tools before execution.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Discovered Installed Applications Section
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DISCOVERED APPS ON DEVICE (${installedApps.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap any discovered application to launch it via JARVIS OPEN_APP tool:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val previewApps = if (installedApps.isNotEmpty()) {
                            installedApps.take(12).map { it.appName }
                        } else {
                            listOf("YouTube", "WhatsApp", "Chrome", "Settings", "Camera", "Gallery", "Maps", "Spotify")
                        }
                        previewApps.forEach { appName ->
                            AssistChip(
                                onClick = { onExecuteCommand("Open $appName") },
                                label = { Text(appName) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Launch,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Registered Action Categories
        items(SAMPLE_TOOL_PROMPTS, key = { it.category.name }) { item ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExecuteCommand(item.sampleVoiceCommand) }
                    .testTag("tool_card_${item.category.name}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.category.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (item.category.defaultRequiresConfirmation) {
                                JarvisAmberWarning.copy(alpha = 0.18f)
                            } else {
                                JarvisEmeraldOnline.copy(alpha = 0.18f)
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = if (item.category.defaultRequiresConfirmation) JarvisAmberWarning else JarvisEmeraldOnline,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (item.category.defaultRequiresConfirmation) "CONFIRMATION GATED" else "DIRECT SAFE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.category.defaultRequiresConfirmation) JarvisAmberWarning else JarvisEmeraldOnline
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Test Command",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "\"${item.sampleVoiceCommand}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
