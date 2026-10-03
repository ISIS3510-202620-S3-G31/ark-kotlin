package com.moviles.ark.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.domain.models.AmbientTrackModel
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.SoreanFontFamily
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

    //al salir (flecha o boton atras del sistema) se guarda la sesion antes de volver (#82)
    val leave = {
        viewModel.finishSession()
        onBack()
    }
    BackHandler(onBack = leave)

    CustomBreathingScreen(
        uiState = uiState,
        onBack = leave,
        onTogglePlayPause = viewModel::togglePlayPause,
        onReset = viewModel::reset,
        onPresetSelected = viewModel::updateDurations,
        onToggleMusic = viewModel::toggleMusic,
        onSelectTrack = viewModel::selectTrack,
        onNextTrack = viewModel::nextTrack,
        onPrevTrack = viewModel::previousTrack,
        onToggleTrackSelector = viewModel::toggleTrackSelector
    )
}

@Composable
fun CustomBreathingScreen(
    uiState: BreathingUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onReset: () -> Unit,
    onPresetSelected: (inhale: Int, hold: Int, exhale: Int) -> Unit,
    onToggleMusic: () -> Unit = {},
    onSelectTrack: (AmbientTrackModel) -> Unit = {},
    onNextTrack: () -> Unit = {},
    onPrevTrack: () -> Unit = {},
    onToggleTrackSelector: () -> Unit = {}
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SecondaryColor)
                        .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Custom Breathing",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = SoreanFontFamily,
                        color = TextColor
                    )
                    Text(
                        text = "Follow the circle rhythm",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FigtreeFontFamily,
                        color = TextColor.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Controlador de musica ambiental Jamendo (#7)
            AmbientMusicCard(
                uiState = uiState,
                onToggleMusic = onToggleMusic,
                onSelectTrack = onSelectTrack,
                onNextTrack = onNextTrack,
                onPrevTrack = onPrevTrack,
                onToggleTrackSelector = onToggleTrackSelector
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Animated Visual Breathing Circle
            BreathingCircleVisualizer(uiState = uiState)

            Spacer(modifier = Modifier.height(28.dp))

            // Cycle Counter Card
            CycleCounterCard(completedCycles = uiState.completedCycles)

            Spacer(modifier = Modifier.height(20.dp))

            // Control Buttons (Play/Pause, Reset)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier
                        .height(50.dp)
                        .weight(1f),
                    border = BorderStroke(1.2.dp, SecondaryColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reset",
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Button(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .height(50.dp)
                        .weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                ) {
                    Icon(
                        imageVector = if (uiState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isRunning) "Pause" else "Start",
                        tint = TextColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isRunning) "Pause" else if (uiState.phase == BreathingPhase.READY) "Start" else "Resume",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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

//tarjeta de reproduccion de musica ambiental de jamendo (#7)
@Composable
private fun AmbientMusicCard(
    uiState: BreathingUiState,
    onToggleMusic: () -> Unit,
    onSelectTrack: (AmbientTrackModel) -> Unit,
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    onToggleTrackSelector: () -> Unit
) {
    val currentTrack = uiState.selectedTrack

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E4CF)),
        border = BorderStroke(1.2.dp, SecondaryColor.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SecondaryColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.isMusicPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                            contentDescription = "Ambient Music",
                            tint = SecondaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AMBIENT MUSIC",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FigtreeFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = SecondaryColor,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )

                        Text(
                            text = currentTrack?.title ?: "Select ambient track",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FigtreeFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = TextColor,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = currentTrack?.artist ?: "Jamendo Audio",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FigtreeFontFamily,
                            color = TextColor.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Botones de control de reproduccion
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPrevTrack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = TextColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SecondaryColor)
                            .clickable { onToggleMusic() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isMusicPlaying) "Pause Music" else "Play Music",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onNextTrack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Track",
                            tint = TextColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleTrackSelector,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Tracks list",
                            tint = if (uiState.showTrackSelector) SecondaryColor else TextColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Carrusel desplegable para cambiar de pista rapidamente
            AnimatedVisibility(visible = uiState.showTrackSelector) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Available Tracks",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextColor.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.ambientTracks) { track ->
                            val isSelected = track.id == currentTrack?.id
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectTrack(track) },
                                color = if (isSelected) SecondaryColor else Color(0xFFEFE0C2),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FigtreeFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else TextColor,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size(220.dp)
                .scale(animatedScale)
                .clip(CircleShape)
                .background(circleColor.copy(alpha = 0.35f))
        )

        // Inner solid circle
        Box(
            modifier = Modifier
                .size(160.dp)
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
                fontFamily = SoreanFontFamily,
                fontWeight = FontWeight.Bold,
                color = TextColor,
                textAlign = TextAlign.Center
            )

            if (uiState.isRunning) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${uiState.secondsRemainingInPhase}s",
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextColor
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = uiState.phase.instruction,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FigtreeFontFamily,
                color = TextColor.copy(alpha = 0.85f),
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E4CF)),
        border = BorderStroke(1.dp, Color(0xFFE2CEB5))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SecondaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SelfImprovement,
                        contentDescription = null,
                        tint = TextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Completed Cycles",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                    Text(
                        text = "Keep going for deeper relaxation",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FigtreeFontFamily,
                        color = TextColor.copy(alpha = 0.7f)
                    )
                }
            }

            Text(
                text = "$completedCycles",
                style = MaterialTheme.typography.headlineLarge,
                fontFamily = FigtreeFontFamily,
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
            fontFamily = FigtreeFontFamily,
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
                fontFamily = FigtreeFontFamily,
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
                secondsRemainingInPhase = 3,
                selectedTrack = AmbientTrackModel("1", "432 Hz Meditation", "Gaia Meditation", "")
            ),
            onBack = {},
            onTogglePlayPause = {},
            onReset = {},
            onPresetSelected = { _, _, _ -> }
        )
    }
}
