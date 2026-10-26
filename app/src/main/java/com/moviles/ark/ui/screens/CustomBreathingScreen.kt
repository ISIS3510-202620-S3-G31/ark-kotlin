package com.moviles.ark.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.data.local.sensors.HapticFeedbackHelper
import com.moviles.ark.domain.models.AmbientTrackModel
import com.moviles.ark.domain.models.BreathingPreset
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

//paleta consistente con los estilos globales de la aplicacion
private val SoftPillInactive = Color(0xFFF3E4CF)
private val SoftCardBorder = TextColor.copy(alpha = 0.12f)

@Composable
fun CustomBreathingRoute(
    viewModel: BreathingViewModel = viewModel(factory = BreathingViewModel.Factory),
    onBack: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hapticHelper = remember(context) { HapticFeedbackHelper(context) }

    //dispara pulsaciones hapticas tactiles sutiles en cada cambio de fase sin interrumpir la sesion
    LaunchedEffect(uiState.phase, uiState.isRunning) {
        if (uiState.isRunning && uiState.phase != BreathingPhase.READY) {
            hapticHelper.triggerPhaseTransition(uiState.phase)
        }
    }

    //guarda la sesion localmente antes de retroceder al home o al catalogo
    val leave = {
        val wasCompleted = viewModel.finishSession()
        onBack(wasCompleted)
    }
    BackHandler(onBack = leave)

    LaunchedEffect(Unit) {
        withFrameNanos { }
        viewModel.onScreenRendered()
    }

    CustomBreathingScreen(
        uiState = uiState,
        onBack = leave,
        onTogglePlayPause = viewModel::togglePlayPause,
        onReset = viewModel::reset,
        onSelectPreset = viewModel::selectPreset,
        onPresetSelected = { inhale, hold, exhale ->
            viewModel.updateDurations(inhale, hold, exhale)
        },
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
    onSelectPreset: (BreathingPreset) -> Unit = {},
    onPresetSelected: (inhale: Int, hold: Int, exhale: Int) -> Unit = { _, _, _ -> },
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
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            //encabezado limpio con boton de retroceso, titulo relajante y pastilla de musica discreta
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SoftPillInactive)
                            .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Mindful Breathing",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = SoreanFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = TextColor
                        )
                        Text(
                            text = "Breathe with the rhythm",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FigtreeFontFamily,
                            color = TextColor.copy(alpha = 0.65f),
                            fontSize = 12.sp
                        )
                    }
                }

                //pastilla discreta de musica ambiental para calmar la mente sin ocupar la mitad de la pantalla
                AmbientMusicCompactPill(
                    uiState = uiState,
                    onToggleMusic = onToggleMusic,
                    onToggleTrackSelector = onToggleTrackSelector
                )
            }

            //selector de pistas desplegable solo cuando el usuario lo solicita
            AnimatedVisibility(visible = uiState.showTrackSelector) {
                AmbientTrackSelectorDrawer(
                    uiState = uiState,
                    onSelectTrack = onSelectTrack,
                    onPrevTrack = onPrevTrack,
                    onNextTrack = onNextTrack
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            //pastillas de seleccion de patrones de respiracion clinica no saturantes
            CalmPresetSelector(
                selectedPreset = uiState.selectedPreset,
                onSelectPreset = onSelectPreset
            )

            Spacer(modifier = Modifier.height(26.dp))

            //circulo de respiracion guiado animado fluidamente a 60 fps en el hilo principal
            CalmBreathingCircleVisualizer(uiState = uiState)

            Spacer(modifier = Modifier.height(22.dp))

            //insignia sutil de progreso y conteo de ciclos completados
            CalmCycleStatusBadge(completedCycles = uiState.completedCycles)

            Spacer(modifier = Modifier.height(24.dp))

            //botones amplios, claros e intuitivos para situaciones de alta ansiedad
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier
                        .height(54.dp)
                        .weight(0.85f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.2.dp, SoftCardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reset",
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .height(54.dp)
                        .weight(1.35f),
                    shape = RoundedCornerShape(20.dp),
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
                        text = if (uiState.isRunning) "Pause" else if (uiState.phase == BreathingPhase.READY) "Start Breathing" else "Resume",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                }
            }
        }
    }
}

//pastilla compacta y no invasiva de musica ambiental en el encabezado
@Composable
private fun AmbientMusicCompactPill(
    uiState: BreathingUiState,
    onToggleMusic: () -> Unit,
    onToggleTrackSelector: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (uiState.isMusicPlaying) SecondaryColor.copy(alpha = 0.22f) else SoftPillInactive,
        border = BorderStroke(1.dp, if (uiState.isMusicPlaying) SecondaryColor.copy(alpha = 0.45f) else SoftCardBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onToggleMusic)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (uiState.isMusicPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                contentDescription = if (uiState.isMusicPlaying) "Pause music" else "Play music",
                tint = if (uiState.isMusicPlaying) SecondaryColor else TextColor.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (uiState.isMusicPlaying) "Playing" else "Music",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FigtreeFontFamily,
                fontWeight = FontWeight.Bold,
                color = TextColor.copy(alpha = 0.85f),
                fontSize = 11.5.sp
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleTrackSelector),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Select track",
                    tint = TextColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

//cajon desplegable limpio para seleccion rapida de pistas sin saturar la pantalla
@Composable
private fun AmbientTrackSelectorDrawer(
    uiState: BreathingUiState,
    onSelectTrack: (AmbientTrackModel) -> Unit,
    onPrevTrack: () -> Unit,
    onNextTrack: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SoftPillInactive),
        border = BorderStroke(1.dp, SoftCardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ambient Soundtracks",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextColor.copy(alpha = 0.8f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevTrack, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = TextColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onNextTrack, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = TextColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.ambientTracks) { track ->
                    val isSelected = track.id == uiState.selectedTrack?.id
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectTrack(track) },
                        color = if (isSelected) SecondaryColor else BackgroundColor,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FigtreeFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextColor,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

//selector de patrones de respiracion guiada minimalista y directo
@Composable
private fun CalmPresetSelector(
    selectedPreset: BreathingPreset,
    onSelectPreset: (BreathingPreset) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BreathingPreset.entries.forEach { preset ->
                val isSelected = selectedPreset == preset
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectPreset(preset) },
                    color = if (isSelected) PrimaryColor else SoftPillInactive,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (isSelected) PrimaryColor else SoftCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when (preset) {
                                BreathingPreset.BOX -> "Box"
                                BreathingPreset.RELAXING -> "4-7-8 Relax"
                                BreathingPreset.QUICK_CALM -> "4-6 Calm"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FigtreeFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = TextColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = preset.patternLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FigtreeFontFamily,
                            color = TextColor.copy(alpha = if (isSelected) 0.85f else 0.6f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        //guia suave de 1 linea para tranquilizar al usuario sin saturarlo de informacion tecnica
        Text(
            text = selectedPreset.formattedDescription,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FigtreeFontFamily,
            color = TextColor.copy(alpha = 0.6f),
            fontSize = 11.5.sp,
            textAlign = TextAlign.Center
        )
    }
}

//circulo de respiracion con transiciones suaves a 60 fps y capas concentricas calmantes
@Composable
private fun CalmBreathingCircleVisualizer(
    uiState: BreathingUiState
) {
    val targetScale = when (uiState.phase) {
        BreathingPhase.READY -> 0.52f
        BreathingPhase.INHALE -> 0.48f + (0.52f * uiState.phaseProgress)
        BreathingPhase.HOLD -> 1.0f
        BreathingPhase.EXHALE -> 1.0f - (0.52f * uiState.phaseProgress)
        BreathingPhase.HOLD_POST_EXHALE -> 0.48f
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 60, easing = LinearEasing),
        label = "calm_breathing_circle_scale"
    )

    //colores amigables y relajantes diferenciados por fase
    val phaseColor = when (uiState.phase) {
        BreathingPhase.READY -> SecondaryColor
        BreathingPhase.INHALE -> PrimaryColor
        BreathingPhase.HOLD -> SecondaryColor
        BreathingPhase.EXHALE -> SuccessColor
        BreathingPhase.HOLD_POST_EXHALE -> SecondaryColor.copy(alpha = 0.85f)
    }

    Box(
        modifier = Modifier.size(250.dp),
        contentAlignment = Alignment.Center
    ) {
        //halo exterior suave y difuso que respira
        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(animatedScale)
                .clip(CircleShape)
                .background(phaseColor.copy(alpha = 0.18f))
        )

        //anillo intermedio que da profundidad visual sin saturar
        Box(
            modifier = Modifier
                .size(190.dp)
                .scale(animatedScale * 0.92f)
                .clip(CircleShape)
                .background(phaseColor.copy(alpha = 0.38f))
        )

        //disco central que enfoca la atencion del usuario
        Box(
            modifier = Modifier
                .size(145.dp)
                .scale(animatedScale * 0.85f)
                .clip(CircleShape)
                .background(phaseColor)
        )

        //texto central con la fase, conteo y frase calmante
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = uiState.phase.label,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = SoreanFontFamily,
                fontWeight = FontWeight.Bold,
                color = TextColor,
                textAlign = TextAlign.Center
            )

            if (uiState.isRunning) {
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
                lineHeight = 13.5.sp,
                maxLines = 2
            )
        }
    }
}

//insignia sutil de conteo de ciclos completados para refuerzo positivo sin presion
@Composable
private fun CalmCycleStatusBadge(completedCycles: Int) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SoftPillInactive,
        border = BorderStroke(1.dp, SoftCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (completedCycles > 0) "$completedCycles cycles completed" else "Follow the rhythm to ground yourself",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FigtreeFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = TextColor.copy(alpha = 0.8f),
                fontSize = 12.sp
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
                completedCycles = 2,
                secondsRemainingInPhase = 4,
                selectedTrack = AmbientTrackModel("1", "432 Hz Meditation", "Gaia Meditation", "")
            ),
            onBack = {},
            onTogglePlayPause = {},
            onReset = {},
            onSelectPreset = {},
            onPresetSelected = { _, _, _ -> }
        )
    }
}
