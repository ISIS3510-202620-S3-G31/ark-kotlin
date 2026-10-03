package com.moviles.ark.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.SuccessColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.BreathingPhase
import com.moviles.ark.ui.viewmodels.BreathingUiState
import com.moviles.ark.ui.viewmodels.BreathingViewModel

@Composable
fun CustomBreathingRoute(
    viewModel: BreathingViewModel = viewModel(factory = BreathingViewModel.Factory),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CustomBreathingScreen(
        uiState = uiState,
        onBack = onBack,
        onTogglePlayPause = viewModel::togglePlayPause,
        onReset = viewModel::reset,
        onPresetSelected = viewModel::updateDurations
    )
}

@Composable
fun CustomBreathingScreen(
    uiState: BreathingUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onReset: () -> Unit,
    onPresetSelected: (inhale: Int, hold: Int, exhale: Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SecondaryColor)
                        .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Custom Breathing",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                    Text(
                        text = "Follow the circle rhythm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextColor.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Animated Visual Breathing Circle
            BreathingCircleVisualizer(uiState = uiState)

            Spacer(modifier = Modifier.height(36.dp))

            // Cycle Counter Card
            CycleCounterCard(completedCycles = uiState.completedCycles)

            Spacer(modifier = Modifier.height(24.dp))

            // Control Buttons (Play/Pause, Reset)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier
                        .height(52.dp)
                        .weight(1f),
                    border = BorderStroke(1.dp, SecondaryColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .height(52.dp)
                        .weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                ) {
                    Icon(
                        imageVector = if (uiState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isRunning) "Pause" else "Start",
                        tint = TextColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isRunning) "Pause" else if (uiState.phase == BreathingPhase.READY) "Start" else "Resume",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Preset Patterns Selector
            PresetPatternsSection(
                currentInhale = uiState.inhaleDuration,
                currentHold = uiState.holdDuration,
                currentExhale = uiState.exhaleDuration,
                onPresetSelected = onPresetSelected
            )
        }
    }
}

@Composable
private fun BreathingCircleVisualizer(
    uiState: BreathingUiState
) {
    val targetScale = when (uiState.phase) {
        BreathingPhase.READY -> 0.5f
        BreathingPhase.INHALE -> 0.45f + (0.55f * uiState.phaseProgress)
        BreathingPhase.HOLD -> 1.0f
        BreathingPhase.EXHALE -> 1.0f - (0.55f * uiState.phaseProgress)
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "breathing_circle_scale"
    )

    val circleColor = when (uiState.phase) {
        BreathingPhase.READY -> SecondaryColor
        BreathingPhase.INHALE -> PrimaryColor
        BreathingPhase.HOLD -> SecondaryColor
        BreathingPhase.EXHALE -> SuccessColor
    }

    Box(
        modifier = Modifier
            .size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(animatedScale)
                .clip(CircleShape)
                .background(circleColor.copy(alpha = 0.35f))
        )

        // Inner solid circle
        Box(
            modifier = Modifier
                .size(170.dp)
                .scale(animatedScale * 0.9f)
                .clip(CircleShape)
                .background(circleColor)
        )

        // Center Text Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = uiState.phase.label,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextColor,
                textAlign = TextAlign.Center
            )

            if (uiState.isRunning) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${uiState.secondsRemainingInPhase}s",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = uiState.phase.instruction,
                style = MaterialTheme.typography.bodySmall,
                color = TextColor.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun CycleCounterCard(completedCycles: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2E3CD)),
        border = BorderStroke(1.dp, Color(0xFFE2CEB5))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SecondaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SelfImprovement,
                        contentDescription = null,
                        tint = TextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Completed Cycles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                    Text(
                        text = "Keep going for deeper relaxation",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextColor.copy(alpha = 0.7f)
                    )
                }
            }

            Text(
                text = "$completedCycles",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryColor
            )
        }
    }
}

@Composable
private fun PresetPatternsSection(
    currentInhale: Int,
    currentHold: Int,
    currentExhale: Int,
    onPresetSelected: (Int, Int, Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Breathing Patterns",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextColor
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetChip(
                label = "Box (4-4-4)",
                isSelected = currentInhale == 4 && currentHold == 4 && currentExhale == 4,
                onClick = { onPresetSelected(4, 4, 4) },
                modifier = Modifier.weight(1f)
            )
            PresetChip(
                label = "Calm (4-7-8)",
                isSelected = currentInhale == 4 && currentHold == 7 && currentExhale == 8,
                onClick = { onPresetSelected(4, 7, 8) },
                modifier = Modifier.weight(1f)
            )
            PresetChip(
                label = "Deep (5-5-5)",
                isSelected = currentInhale == 5 && currentHold == 5 && currentExhale == 5,
                onClick = { onPresetSelected(5, 5, 5) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = if (isSelected) PrimaryColor else Color(0xFFEFE0C2),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = TextColor,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CustomBreathingScreenPreview() {
    AppTheme {
        CustomBreathingScreen(
            uiState = BreathingUiState(
                phase = BreathingPhase.INHALE,
                isRunning = true,
                completedCycles = 3,
                secondsRemainingInPhase = 3
            ),
            onBack = {},
            onTogglePlayPause = {},
            onReset = {},
            onPresetSelected = { _, _, _ -> }
        )
    }
}
