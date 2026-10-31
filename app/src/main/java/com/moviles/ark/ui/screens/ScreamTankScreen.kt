package com.moviles.ark.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.R
import com.moviles.ark.domain.models.DecibelMeter
import com.moviles.ark.domain.models.Loudness
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.ScreenTopPadding
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.MicPermissionState
import com.moviles.ark.ui.viewmodels.ScreamTankUiState
import com.moviles.ark.ui.viewmodels.ScreamTankViewModel

//disenio del figma "Mockups - Moviles" (Scream Tank), sin el escenario oscuro, las ondas ni el porcentaje: el tanque grande y centrado, medidor de 12 segmentos y tres botones
//solo colores de la paleta (ms6) y estilos del tema: titulo en Sorean, el resto Figtree

//medidas del cuerpo del tanque en el figma
private val TankWidth = 115.dp
private val TankHeight = 246.dp
//cuanto puede crecer el tanque sobre su medida del figma
private const val MAX_TANK_SCALE = 1.6f

//el medidor de abajo tiene 12 segmentos y el del tanque 10 celdas
private const val METER_SEGMENTS = 12
private const val TANK_CELLS = 10

//conecta la pantalla con el viewmodel
@Composable
fun ScreamTankRoute(
    viewModel: ScreamTankViewModel = viewModel(factory = ScreamTankViewModel.Factory),
    onBack: (wasCompleted: Boolean, durationSeconds: Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    //al salir (flecha, boton de terminar o atras del sistema) se guarda la sesion antes de volver
    val leave = {
        val (wasCompleted, durationSeconds) = viewModel.finishSessionWithDuration()
        onBack(wasCompleted, durationSeconds)
    }
    BackHandler(onBack = leave)

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        //si lo nego y android ya no deja mostrar la explicacion, marco "no volver a preguntar"
        val activity = context.findActivity()
        val canAskAgain = activity == null ||
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        viewModel.onPermissionResult(isGranted, canAskAgain)
    }

    //al volver de ajustes se revisa el permiso; al salir de la app se suelta el microfono
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermission() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pauseListening() }

    //latencia: se espera al siguiente cuadro; para entonces el primer dibujo de la pantalla ya termino
    LaunchedEffect(Unit) {
        withFrameNanos { }
        viewModel.onScreenRendered()
    }

    ScreamTankScreen(
        uiState = uiState,
        onBack = leave,
        onToggleListening = viewModel::toggleListening,
        onRestart = viewModel::restartSession,
        onFinish = leave,
        onShowPermissionDialog = viewModel::showPermissionDialog,
        onRequestPermission = { microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onOpenSettings = {
            viewModel.dismissPermissionDialog()
            context.openAppSettings()
        },
        onDismissPermissionDialog = viewModel::dismissPermissionDialog
    )
}

//pantalla sin estado: solo dibuja lo que le llega en uiState y avisa los clics
@Composable
fun ScreamTankScreen(
    uiState: ScreamTankUiState,
    onBack: () -> Unit,
    onToggleListening: () -> Unit,
    onFinish: () -> Unit,
    onRestart: () -> Unit = {},
    onShowPermissionDialog: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onDismissPermissionDialog: () -> Unit = {}
) {
    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            ScreamHeader(onBack = onBack)
            ScreamStage(
                uiState = uiState,
                onShowPermissionDialog = onShowPermissionDialog,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
            IntensityMeter(level = uiState.currentLevel)
            ScreamControls(
                uiState = uiState,
                onRestart = onRestart,
                onToggleListening = onToggleListening,
                onFinish = onFinish
            )
        }
    }

    if (uiState.showPermissionDialog) {
        MicrophonePermissionDialog(
            isPermanentlyDenied = uiState.permission == MicPermissionState.PERMANENTLY_DENIED,
            onRequestPermission = onRequestPermission,
            onOpenSettings = onOpenSettings,
            onDismiss = onDismissPermissionDialog
        )
    }
}

//boton redondo de 48 dp (minimo tactil) en secondary y el titulo de la herramienta
@Composable
private fun ScreamHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = ScreenTopPadding, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RoundIconButton(iconRes = R.drawable.ic_scream_chevron_left, label = "Back", onClick = onBack)
        Text("Scream Tank", style = MaterialTheme.typography.headlineLarge, color = TextColor)
    }
}

//escenario: aviso de estado arriba, el tanque en el centro y una frase debajo, todo centrado
@Composable
private fun ScreamStage(
    uiState: ScreamTankUiState,
    onShowPermissionDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasMicrophone = uiState.permission == MicPermissionState.GRANTED
    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            TankIllustration(
                level = uiState.currentLevel,
                peakLevel = uiState.peakLevel,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = stageMessage(uiState),
                style = MaterialTheme.typography.headlineSmall,
                color = TextColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(270.dp)
            )
            if (!hasMicrophone) {
                TextButton(onClick = onShowPermissionDialog) {
                    Text("Allow microphone", style = MaterialTheme.typography.labelLarge, color = PrimaryColor)
                }
            }
            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AccentColor,
                    textAlign = TextAlign.Center
                )
            }
        }
        StatusPill(
            uiState = uiState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        )
    }
}

//frase debajo del tanque segun lo que esta pasando
private fun stageMessage(uiState: ScreamTankUiState): String = when {
    uiState.permission != MicPermissionState.GRANTED -> "The meter needs the microphone. Nothing is recorded."
    uiState.isListening -> "Let it all out!"
    uiState.hasStarted -> "Paused. Tap the mic to keep going."
    else -> "Tap the mic and let it all out."
}

//aviso "Listening": el punto rojo solo se ve mientras el microfono escucha
@Composable
private fun StatusPill(uiState: ScreamTankUiState, modifier: Modifier = Modifier) {
    val label = when {
        uiState.isListening -> "Listening"
        uiState.hasStarted -> "Paused"
        else -> "Ready"
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(TextColor.copy(alpha = 0.06f))
            .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (uiState.isListening) AccentColor else TextColor.copy(alpha = 0.3f))
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextColor)
    }
}

//el tanque del figma centrado: bandas naranjas, tapas grises y un tubo oscuro con 10 celdas que se llenan con el volumen
@Composable
private fun TankIllustration(level: Int, peakLevel: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        //el dibujo crece hasta llenar el espacio libre (con tope) y se achica si no cabe, sin deformarse
        val scale = minOf(MAX_TANK_SCALE, maxWidth / TankWidth, if (maxHeight == Dp.Infinity) 1f else maxHeight / TankHeight)
        Box(
            modifier = Modifier
                .size(TankWidth * scale, TankHeight * scale)
                .clearAndSetSemantics { contentDescription = "Current level $level of 100, peak $peakLevel" },
            contentAlignment = Alignment.Center
        ) {
            AcousticContainer(
                level = level,
                modifier = Modifier
                    .requiredSize(TankWidth, TankHeight)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )
        }
    }
}

//cuerpo del tanque con las mismas medidas del figma (115 x 246)
@Composable
private fun AcousticContainer(level: Int, modifier: Modifier = Modifier) {
    //animacion corta para que las celdas no salten entre lecturas (llegan cada 50 ms)
    val animatedLevel by animateFloatAsState(
        targetValue = level.toFloat(),
        animationSpec = tween(durationMillis = 90),
        label = "tankLevel"
    )
    val litCells = filledUnits(animatedLevel.toInt(), TANK_CELLS)
    Box(modifier = modifier.size(115.dp, 246.dp)) {
        TankBand(y = 6.5.dp, height = 26.dp)
        TankBand(y = 47.5.dp, height = 151.dp)
        TankBand(y = 213.5.dp, height = 26.dp)
        Image(
            painter = painterResource(R.drawable.scream_tank_cap_top),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(y = 0.5.dp)
                .size(115.826.dp, 47.5.dp)
        )
        Image(
            painter = painterResource(R.drawable.scream_tank_cap_bottom),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(y = 198.dp)
                .size(115.826.dp, 47.5.dp)
                .rotate(180f)
        )
        //tubo oscuro con las celdas: se llenan de abajo hacia arriba, naranja las primeras y rojo las fuertes
        Box(
            modifier = Modifier
                .offset(x = 50.dp, y = 61.5.dp)
                .size(15.dp, 115.dp)
                .clip(RoundedCornerShape(90.dp))
                .background(TextColor)
        )
        Column(
            modifier = Modifier
                .offset(x = 51.5.dp, y = 66.5.dp)
                .size(12.dp, 105.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (cell in TANK_CELLS downTo 1) {
                val color = when {
                    cell > litCells -> BackgroundColor.copy(alpha = 0.15f)
                    cell > TANK_CELLS / 2 -> AccentColor
                    else -> PrimaryColor
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun TankBand(y: Dp, height: Dp) {
    Box(
        modifier = Modifier
            .offset(x = 1.dp, y = y)
            .size(113.dp, height)
            .background(PrimaryColor)
    )
}

//cuantas celdas o segmentos se encienden con un volumen de 0 a 100
private fun filledUnits(level: Int, units: Int): Int =
    ((level.coerceIn(0, DecibelMeter.MAX_LEVEL) * units + DecibelMeter.MAX_LEVEL - 1) / DecibelMeter.MAX_LEVEL)

//nombre de cada nivel de volumen; los umbrales son del modelo (DecibelMeter)
private fun loudnessLabel(loudness: Loudness): String = when (loudness) {
    Loudness.SOFT -> "Soft"
    Loudness.LOUD -> "Medium"
    Loudness.VERY_LOUD -> "Strong"
}

//medidor de 12 segmentos: los primeros en secondary, los del medio en primary y los ultimos en accent
@Composable
private fun IntensityMeter(level: Int) {
    val litSegments = filledUnits(level, METER_SEGMENTS)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp)
            .clearAndSetSemantics { contentDescription = "Intensity $level of 100, ${loudnessLabel(DecibelMeter.loudnessOf(level))}" },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Intensity", style = MaterialTheme.typography.bodyLarge, color = TextColor)
            Text(
                text = "$level · ${loudnessLabel(DecibelMeter.loudnessOf(level))}",
                style = MaterialTheme.typography.headlineSmall,
                color = TextColor
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (segment in 1..METER_SEGMENTS) {
                val color = when {
                    segment > litSegments -> TextColor.copy(alpha = 0.12f)
                    segment <= 4 -> SecondaryColor
                    segment <= 8 -> PrimaryColor
                    else -> AccentColor
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Soft", style = MaterialTheme.typography.bodyLarge, color = TextColor)
            Text("Medium", style = MaterialTheme.typography.bodyLarge, color = TextColor)
            Text("Strong", style = MaterialTheme.typography.bodyLarge, color = TextColor)
        }
    }
}

//reiniciar, microfono (escuchar o pausar) y terminar
@Composable
private fun ScreamControls(
    uiState: ScreamTankUiState,
    onRestart: () -> Unit,
    onToggleListening: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(
            iconRes = R.drawable.ic_scream_restart,
            label = "Restart",
            enabled = uiState.hasStarted,
            onClick = onRestart
        )
        MicButton(level = uiState.currentLevel, isListening = uiState.isListening, onClick = onToggleListening)
        RoundIconButton(
            iconRes = R.drawable.ic_scream_check,
            label = "Finish",
            enabled = uiState.hasStarted,
            onClick = onFinish
        )
    }
}

//boton del microfono de 116 dp: mientras escucha, los dos anillos crecen con el volumen
@Composable
private fun MicButton(level: Int, isListening: Boolean, onClick: () -> Unit) {
    val ringGrowth by animateFloatAsState(
        targetValue = if (isListening) 0.7f + 0.3f * (level / DecibelMeter.MAX_LEVEL.toFloat()) else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "micRings"
    )
    Box(
        modifier = Modifier
            .size(116.dp)
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClickLabel = if (isListening) "Pause listening" else "Start listening",
                onClick = onClick
            )
            .semantics { contentDescription = if (isListening) "Pause listening" else "Start listening" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(116.dp * ringGrowth)
                .clip(CircleShape)
                .background(PrimaryColor.copy(alpha = 0.15f))
        )
        Box(
            modifier = Modifier
                .size(96.dp * ringGrowth)
                .clip(CircleShape)
                .background(PrimaryColor.copy(alpha = 0.3f))
        )
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(PrimaryColor)
        )
        Image(
            painter = painterResource(R.drawable.ic_scream_mic),
            contentDescription = null,
            modifier = Modifier.size(116.dp)
        )
    }
}

//boton redondo de 48 dp en secondary con un icono de 22 dp
@Composable
private fun RoundIconButton(iconRes: Int, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(24.dp))
            .background(SecondaryColor)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(22.dp))
    }
}

//explica por que se pide el microfono; si esta bloqueado, como activarlo en ajustes
@Composable
private fun MicrophonePermissionDialog(
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundColor,
        icon = { Icon(Icons.Filled.Mic, contentDescription = null, tint = SecondaryColor) },
        title = {
            Text(
                if (isPermanentlyDenied) "Microphone is blocked" else "Allow the microphone",
                style = MaterialTheme.typography.headlineSmall,
                color = TextColor
            )
        },
        text = {
            Text(
                if (isPermanentlyDenied) {
                    "To use the Scream tank, open Settings, go to Permissions, tap Microphone and choose Allow. Ark never records or saves your voice."
                } else {
                    "The Scream tank uses the microphone to show how loud you are in real time. The sound is only measured in memory: it is never recorded, saved or sent."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextColor
            )
        },
        confirmButton = {
            Button(
                onClick = if (isPermanentlyDenied) onOpenSettings else onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = TextColor)
            ) {
                Text(if (isPermanentlyDenied) "Open settings" else "Allow microphone", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now", style = MaterialTheme.typography.labelLarge, color = TextColor)
            }
        }
    )
}

//el context de compose puede venir envuelto; se busca la activity para preguntar por el permiso
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

//abre la pagina de la app en ajustes; si el telefono no la tiene, no hace nada en vez de cerrarse
private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    if (findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

@Preview(showBackground = true, name = "Scream tank - listening", widthDp = 390, heightDp = 844)
@Composable
fun ScreamTankListeningPreview() {
    AppTheme {
        ScreamTankScreen(
            uiState = ScreamTankUiState(
                permission = MicPermissionState.GRANTED,
                isListening = true,
                hasStarted = true,
                currentLevel = 70,
                currentDbfs = -16.8,
                peakLevel = 84,
                elapsedSeconds = 14
            ),
            onBack = {}, onToggleListening = {}, onFinish = {}
        )
    }
}
