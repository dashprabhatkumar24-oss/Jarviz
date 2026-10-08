package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JarvisAmberWarning
import com.example.ui.theme.JarvisBlueSecondary
import com.example.ui.theme.JarvisCrimsonAlert
import com.example.ui.theme.JarvisCyanBright
import com.example.ui.theme.JarvisCyanPrimary
import com.example.ui.theme.JarvisEmeraldOnline
import com.example.voice.VoiceOrbState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvisHudOrb(
    orbState: VoiceOrbState,
    audioLevelRms: Float,
    assistantName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    orbSize: Dp = 210.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_orb_transition")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (orbState) {
                    VoiceOrbState.THINKING -> 2800
                    VoiceOrbState.LISTENING -> 5000
                    VoiceOrbState.SPEAKING -> 4000
                    else -> 14000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_ring_rotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (orbState) {
                    VoiceOrbState.THINKING -> 2000
                    VoiceOrbState.SPEAKING -> 3200
                    else -> 10000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_ring_rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (orbState) {
                    VoiceOrbState.LISTENING -> 650
                    VoiceOrbState.SPEAKING -> 480
                    VoiceOrbState.THINKING -> 550
                    VoiceOrbState.CONFIRMATION -> 700
                    VoiceOrbState.IDLE -> 2200
                }
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    val primaryCoreColor = when (orbState) {
        VoiceOrbState.IDLE -> JarvisCyanPrimary
        VoiceOrbState.LISTENING -> JarvisEmeraldOnline
        VoiceOrbState.THINKING -> JarvisAmberWarning
        VoiceOrbState.SPEAKING -> JarvisCyanBright
        VoiceOrbState.CONFIRMATION -> JarvisCrimsonAlert
    }

    val secondaryRingColor = when (orbState) {
        VoiceOrbState.IDLE -> JarvisBlueSecondary
        VoiceOrbState.LISTENING -> JarvisCyanPrimary
        VoiceOrbState.THINKING -> JarvisCyanBright
        VoiceOrbState.SPEAKING -> JarvisBlueSecondary
        VoiceOrbState.CONFIRMATION -> JarvisAmberWarning
    }

    val statusText = when (orbState) {
        VoiceOrbState.IDLE -> "TAP TO SPEAK"
        VoiceOrbState.LISTENING -> "LISTENING..."
        VoiceOrbState.THINKING -> "ANALYZING..."
        VoiceOrbState.SPEAKING -> "SPEAKING (TAP TO INTERRUPT)"
        VoiceOrbState.CONFIRMATION -> "CONFIRMATION REQUIRED"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(orbSize)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = primaryCoreColor),
                    onClick = onClick
                )
                .semantics {
                    contentDescription = "Activate $assistantName voice assistant or interrupt speech"
                }
                .testTag("jarvis_orb_button")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f
                val rmsBoost = if (orbState == VoiceOrbState.LISTENING) audioLevelRms * 18f else 0f

                // 1. Deep radial glow halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryCoreColor.copy(alpha = 0.35f),
                            secondaryRingColor.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = (maxRadius * pulseScale).coerceAtLeast(10f)
                    ),
                    radius = maxRadius,
                    center = center
                )

                // 2. Outer segmented telemetry ring
                rotate(degrees = outerRotation, pivot = center) {
                    val outerRingRadius = maxRadius * 0.88f
                    val arcTopLeft = Offset(center.x - outerRingRadius, center.y - outerRingRadius)
                    val arcSize = Size(outerRingRadius * 2f, outerRingRadius * 2f)
                    for (angle in listOf(0f, 90f, 180f, 270f)) {
                        drawArc(
                            color = primaryCoreColor.copy(alpha = 0.75f),
                            startAngle = angle + 8f,
                            sweepAngle = 58f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // 3. Tick marks ring
                val tickRadiusOuter = maxRadius * 0.78f
                val tickRadiusInner = maxRadius * 0.73f
                for (i in 0 until 36) {
                    val rad = Math.toRadians((i * 10).toDouble())
                    val start = Offset(
                        x = center.x + (tickRadiusInner * cos(rad)).toFloat(),
                        y = center.y + (tickRadiusInner * sin(rad)).toFloat()
                    )
                    val end = Offset(
                        x = center.x + (tickRadiusOuter * cos(rad)).toFloat(),
                        y = center.y + (tickRadiusOuter * sin(rad)).toFloat()
                    )
                    drawLine(
                        color = secondaryRingColor.copy(alpha = if (i % 3 == 0) 0.7f else 0.3f),
                        start = start,
                        end = end,
                        strokeWidth = if (i % 3 == 0) 2.dp.toPx() else 1.dp.toPx()
                    )
                }

                // 4. Counter-rotating inner tactical ring
                rotate(degrees = innerRotation, pivot = center) {
                    val innerRingRadius = maxRadius * 0.62f + rmsBoost
                    val arcTopLeft = Offset(center.x - innerRingRadius, center.y - innerRingRadius)
                    val arcSize = Size(innerRingRadius * 2f, innerRingRadius * 2f)
                    for (angle in listOf(30f, 150f, 270f)) {
                        drawArc(
                            color = secondaryRingColor.copy(alpha = 0.85f),
                            startAngle = angle,
                            sweepAngle = 75f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // 5. Inner pulsing energy core
                val coreRadius = (maxRadius * 0.38f * pulseScale) + rmsBoost
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryCoreColor.copy(alpha = 0.85f),
                            primaryCoreColor.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = coreRadius.coerceAtLeast(8f)
                    ),
                    radius = coreRadius,
                    center = center
                )

                drawCircle(
                    color = primaryCoreColor,
                    radius = maxRadius * 0.34f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Center Icon inside the HUD Orb
            Icon(
                imageVector = when (orbState) {
                    VoiceOrbState.SPEAKING -> Icons.Default.Stop
                    VoiceOrbState.LISTENING -> Icons.Default.GraphicEq
                    VoiceOrbState.CONFIRMATION -> Icons.Default.Security
                    else -> Icons.Default.Mic
                },
                contentDescription = null,
                tint = primaryCoreColor,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.labelLarge,
            color = primaryCoreColor,
            modifier = Modifier.testTag("orb_status_label")
        )
    }
}
