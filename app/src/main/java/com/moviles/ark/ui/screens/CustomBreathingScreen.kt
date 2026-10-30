package com.moviles.ark.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.domain.models.AmbientTrackModel
import com.moviles.ark.domain.models.BreathingPreset
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.domain.models.BreathingPattern
import com.moviles.ark.domain.models.BreathingPhase
import com.moviles.ark.ui.viewmodels.BreathingUiState
import com.moviles.ark.ui.viewmodels.BreathingViewModel

//vista minimalista del figma "Diseno Custom Breathing": el circulo es lo unico que pide atencion
//solo colores de la paleta (ms6), peso 400 y estilos del tema: H1 (Sorean) en el titulo y la fase, el resto Figtree

@Composable
fun CustomBreathingRoute(
    viewModel: BreathingViewModel = viewModel(factory = BreathingViewModel.Factory),
    onBack: (wasCompleted: Boolean, durationSeconds: Int) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    //la vibracion de cada etapa la dispara el viewmodel

    //atras (boton o gesto) = abandonar: se guarda como no terminada y se vuelve sin feedback
    val leave = { onBack(false, viewModel.abandonSession()) }
    //Finish = terminar: se guarda como terminada y la navegacion abre el feedback
    val finish = {
        val (wasCompleted, durationSeconds) = viewModel.finishSession()
        onBack(wasCompleted, durationSeconds)
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
        onFinish = finish,
        onSelectPreset = viewModel::selectPreset,
        onSelectCustom = viewModel::selectCustom,
        onEditCustom = viewModel::openCustomRhythmSheet,
        onDismissCustom = viewModel::dismissCustomRhythmSheet,
        onSaveCustom = viewModel::saveCustomRhythm,
        onToggleTrackSelector = viewModel::toggleTrackSelector,
        onTrackTapped = viewModel::onTrackTapped
    )
}

@Composable
fun CustomBreathingScreen(
    uiState: BreathingUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onReset: () -> Unit,
    onFinish: () -> Unit = {},
    onSelectPreset: (BreathingPreset) -> Unit = {},
    onSelectCustom: () -> Unit = {},
    onEditCustom: () -> Unit = {},
    onDismissCustom: () -> Unit = {},
    onSaveCustom: (inhale: Int, hold: Int, exhale: Int) -> Unit = { _, _, _ -> },
    onToggleTrackSelector: () -> Unit = {},
    onTrackTapped: (AmbientTrackModel) -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BreathingTopBar(
                uiState = uiState,
                onBack = onBack,
                onToggleTrackSelector = onToggleTrackSelector
            )

            //el circulo queda centrado en todo el espacio libre del medio
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                BreathingCircle(uiState = uiState)
            }

            //debajo del circulo y centrado en la pantalla: el patron (antes de empezar) o los ciclos (mientras corre)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!uiState.isRunning && uiState.phase == BreathingPhase.READY) {
                    PatternSelector(
                        uiState = uiState,
                        onSelectPreset = onSelectPreset,
                        onSelectCustom = onSelectCustom,
                        onEditCustom = onEditCustom,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (uiState.completedCycles > 0) {
                    Text(
                        text = if (uiState.completedCycles == 1) "1 cycle" else "${uiState.completedCycles} cycles",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            //abajo del todo: reiniciar, iniciar/pausar y terminar
            BreathingControls(
                uiState = uiState,
                onReset = onReset,
                onTogglePlayPause = onTogglePlayPause,
                onFinish = onFinish
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (uiState.showTrackSelector) {
        AmbientMusicSheet(
            uiState = uiState,
            onDismiss = onToggleTrackSelector,
            onTrackTapped = onTrackTapped
        )
    }

    if (uiState.showCustomRhythmSheet) {
        CustomRhythmSheet(
            uiState = uiState,
            onDismiss = onDismissCustom,
            onDone = onSaveCustom
        )
    }
}

//boton atras redondo secondary, titulo en H1 y la musica arriba a la derecha
@Composable
private fun BreathingTopBar(
    uiState: BreathingUiState,
    onBack: () -> Unit,
    onToggleTrackSelector: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 20.dp),
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
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        //en pantallas angostas el titulo se achica lo justo para caber junto a la musica en vez de cortarse
        BasicText(
            text = "Custom Breathing",
            style = MaterialTheme.typography.headlineLarge.copy(color = TextColor),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = MIN_TITLE_FONT_SIZE,
                maxFontSize = MaterialTheme.typography.headlineLarge.fontSize
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        //borde = musica en pausa; relleno con ondas = sonando. Tocarlo abre la hoja con las pistas
        RoundIconButton(
            icon = if (uiState.isMusicPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
            contentDescription = if (uiState.isMusicPlaying) "Ambient music playing, choose track" else "Ambient music",
            filled = uiState.isMusicPlaying,
            onClick = onToggleTrackSelector
        )
    }
}

//circulo de un solo color (secondary) que crece al inhalar y se encoge al exhalar
//el anillo punteado marca el tamano maximo; las instrucciones van solo para TalkBack
@Composable
private fun BreathingCircle(uiState: BreathingUiState) {
    val targetScale = when (uiState.phase) {
        BreathingPhase.READY -> MIN_CIRCLE_SCALE
        BreathingPhase.INHALE -> MIN_CIRCLE_SCALE + (1f - MIN_CIRCLE_SCALE) * uiState.phaseProgress
        BreathingPhase.HOLD -> 1f
        BreathingPhase.EXHALE -> 1f - (1f - MIN_CIRCLE_SCALE) * uiState.phaseProgress
        BreathingPhase.HOLD_POST_EXHALE -> MIN_CIRCLE_SCALE
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 60, easing = LinearEasing),
        label = "breathing_circle_scale"
    )

    val accessibilityText = "${uiState.phase.label}. ${uiState.phase.instruction}. " +
        "Inhale ${uiState.inhaleDuration} seconds, hold ${uiState.holdDuration} seconds, exhale ${uiState.exhaleDuration} seconds"

    Box(
        modifier = Modifier
            .size(CIRCLE_SIZE)
            .semantics { contentDescription = accessibilityText },
        contentAlignment = Alignment.Center
    ) {
        //anillo guia punteado
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 1.5.dp.toPx()
            drawCircle(
                color = SecondaryColor,
                radius = size.minDimension / 2f - strokeWidth / 2f,
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                )
            )
        }

        //graphicsLayer escala sin volver a medir el layout en cada cuadro
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                }
                .clip(CircleShape)
                .background(SecondaryColor)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = uiState.phase.label,
                style = MaterialTheme.typography.headlineLarge,
                color = TextColor
            )
            //los segundos solo se ven mientras corre; en pausa queda la palabra sola
            if (uiState.isRunning) {
                Text(
                    text = "${uiState.secondsRemainingInPhase}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextColor
                )
            }
        }
    }
}

//pastilla Box · Calm · Deep · Custom; Custom seleccionado muestra un lapiz para editar el ritmo
@Composable
private fun PatternSelector(
    uiState: BreathingUiState,
    onSelectPreset: (BreathingPreset) -> Unit,
    onSelectCustom: () -> Unit,
    onEditCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .border(1.dp, SecondaryColor, CircleShape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SELECTOR_PRESETS.forEach { (label, preset) ->
            PatternOption(
                label = label,
                isSelected = !uiState.isCustomSelected && uiState.selectedPreset == preset,
                onClick = { onSelectPreset(preset) },
                modifier = Modifier.weight(1f)
            )
        }
        PatternOption(
            label = "Custom",
            isSelected = uiState.isCustomSelected,
            showEditIcon = uiState.isCustomSelected,
            //con Custom ya elegido, tocarlo otra vez abre la hoja para editar el ritmo
            onClick = if (uiState.isCustomSelected) onEditCustom else onSelectCustom,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PatternOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showEditIcon: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(if (isSelected) SecondaryColor else BackgroundColor)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = TextColor, maxLines = 1)
        if (showEditIcon) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit custom rhythm",
                tint = TextColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

//reiniciar · play/pausa · terminar, abajo y centrados
//reiniciar solo aparece cuando hay algo que reiniciar; terminar se habilita desde el primer ciclo completo
@Composable
private fun BreathingControls(
    uiState: BreathingUiState,
    onReset: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LabeledRoundButton(
            icon = Icons.Filled.Refresh,
            label = "Reset",
            onClick = onReset,
            visible = uiState.canReset
        )

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PrimaryColor)
                .clickable(role = Role.Button, onClick = onTogglePlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (uiState.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = when {
                    uiState.isRunning -> "Pause"
                    uiState.phase == BreathingPhase.READY -> "Start breathing"
                    else -> "Resume"
                },
                tint = TextColor,
                modifier = Modifier.size(32.dp)
            )
        }

        LabeledRoundButton(
            icon = Icons.Filled.Check,
            label = "Finish",
            onClick = onFinish,
            enabled = uiState.canFinish,
            contentDescription = if (uiState.canFinish) "Finish session" else "Finish session, complete one cycle first"
        )
    }
}

//boton redondo con su nombre debajo; invisible ocupa el mismo espacio para que el play no se mueva
@Composable
private fun LabeledRoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    visible: Boolean = true,
    enabled: Boolean = true,
    contentDescription: String = label
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .alpha(if (visible) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RoundIconButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = onClick,
            enabled = visible && enabled
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TextColor,
            modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)
        )
    }
}

//boton redondo de 48 dp: con borde secondary o relleno secondary
@Composable
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    filled: Boolean = false,
    size: Dp = 48.dp,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(size)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .background(if (filled) SecondaryColor else BackgroundColor)
            .border(1.dp, SecondaryColor, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = TextColor, modifier = Modifier.size(22.dp))
    }
}

//hoja inferior con las pistas: la que suena va en secondary con ondas; tocar una la reproduce, tocar la que suena la pausa
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmbientMusicSheet(
    uiState: BreathingUiState,
    onDismiss: () -> Unit,
    onTrackTapped: (AmbientTrackModel) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BackgroundColor,
        contentColor = TextColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                text = "Ambient music",
                style = MaterialTheme.typography.headlineSmall,
                color = TextColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            when {
                uiState.isLoadingMusic && uiState.ambientTracks.isEmpty() -> SheetMessage("Loading music…")
                uiState.ambientTracks.isEmpty() -> SheetMessage("No music available right now")
                else -> uiState.ambientTracks.forEach { track ->
                    val isPlaying = uiState.isMusicPlaying && track.id == uiState.selectedTrack?.id
                    TrackRow(track = track, isPlaying = isPlaying, onClick = { onTrackTapped(track) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: AmbientTrackModel, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (isPlaying) SecondaryColor else BackgroundColor)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
            contentDescription = if (isPlaying) "Playing" else null,
            tint = TextColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge,
            color = TextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SheetMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = TextColor,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

//hoja "Custom rhythm": - / + para inhale, hold y exhale; el ritmo se aplica al tocar Done
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRhythmSheet(
    uiState: BreathingUiState,
    onDismiss: () -> Unit,
    onDone: (inhale: Int, hold: Int, exhale: Int) -> Unit
) {
    var inhale by remember { mutableIntStateOf(uiState.customInhale) }
    var hold by remember { mutableIntStateOf(uiState.customHold) }
    var exhale by remember { mutableIntStateOf(uiState.customExhale) }
    //los rangos del ritmo Custom son reglas del modelo
    val breathRange = BreathingPattern.CUSTOM_BREATH_RANGE
    val holdRange = BreathingPattern.CUSTOM_HOLD_RANGE

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BackgroundColor,
        contentColor = TextColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                text = "Custom rhythm",
                style = MaterialTheme.typography.headlineSmall,
                color = TextColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            StepperRow(label = "Inhale", value = inhale, range = breathRange, onValueChange = { inhale = it })
            Spacer(modifier = Modifier.height(8.dp))
            StepperRow(label = "Hold", value = hold, range = holdRange, onValueChange = { hold = it })
            Spacer(modifier = Modifier.height(8.dp))
            StepperRow(label = "Exhale", value = exhale, range = breathRange, onValueChange = { exhale = it })
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onDone(inhale, hold, exhale) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = TextColor)
            ) {
                Text(text = "Done", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextColor, modifier = Modifier.weight(1f))
        StepperButton(
            icon = Icons.Filled.Remove,
            contentDescription = "Less $label",
            enabled = value > range.first,
            onClick = { onValueChange((value - 1).coerceIn(range)) }
        )
        Text(
            text = "$value s",
            style = MaterialTheme.typography.headlineMedium,
            color = TextColor,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center
        )
        StepperButton(
            icon = Icons.Filled.Add,
            contentDescription = "More $label",
            enabled = value < range.last,
            onClick = { onValueChange((value + 1).coerceIn(range)) }
        )
    }
}

@Composable
private fun StepperButton(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = BackgroundColor,
        border = BorderStroke(1.dp, SecondaryColor),
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = TextColor, modifier = Modifier.size(20.dp))
        }
    }
}

//nombres cortos del selector: Calm = 4-6, Deep = 4-7-8
private val SELECTOR_PRESETS = listOf(
    "Box" to BreathingPreset.BOX,
    "Calm" to BreathingPreset.QUICK_CALM,
    "Deep" to BreathingPreset.RELAXING
)

private val CIRCLE_SIZE = 260.dp
private val MIN_TITLE_FONT_SIZE = 20.sp
//en reposo el circulo ocupa la mitad del anillo
private const val MIN_CIRCLE_SCALE = 0.5f

//botones que todavia no se pueden usar (Finish antes del primer ciclo)
private const val DISABLED_ALPHA = 0.4f

@Preview(showBackground = true, name = "Ready")
@Composable
fun CustomBreathingScreenPreview() {
    AppTheme {
        CustomBreathingScreen(
            uiState = BreathingUiState(),
            onBack = {},
            onTogglePlayPause = {},
            onReset = {}
        )
    }
}

@Preview(showBackground = true, name = "Hold with music")
@Composable
fun CustomBreathingRunningPreview() {
    AppTheme {
        CustomBreathingScreen(
            uiState = BreathingUiState(
                phase = BreathingPhase.HOLD,
                isRunning = true,
                completedCycles = 2,
                secondsRemainingInPhase = 2,
                isMusicPlaying = true
            ),
            onBack = {},
            onTogglePlayPause = {},
            onReset = {}
        )
    }
}
