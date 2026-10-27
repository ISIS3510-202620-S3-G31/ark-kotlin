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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.domain.models.DecibelMeter
import com.moviles.ark.domain.models.Loudness
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.ErrorColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.MicPermissionState
import com.moviles.ark.ui.viewmodels.ScreamTankUiState
import com.moviles.ark.ui.viewmodels.ScreamTankViewModel
import java.util.Locale

//solo se usan colores de la paleta (ms6); las tarjetas son el texto muy transparente sobre el fondo
private val ScreamCardFill = TextColor.copy(alpha = 0.06f)

//el arco del medidor empieza abajo a la izquierda y da 3/4 de vuelta
private const val GAUGE_START_ANGLE = 135f
private const val GAUGE_SWEEP_ANGLE = 270f

//conecta la pantalla con el viewmodel
@Composable
fun ScreamTankRoute(
    viewModel: ScreamTankViewModel = viewModel(factory = ScreamTankViewModel.Factory),
    onBack: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    //al salir (flecha, boton finish o atras del sistema) se guarda la sesion antes de volver
    val leave = {
        val wasCompleted = viewModel.finishSession()
        onBack(wasCompleted)
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScreamHeader(onBack = onBack)
            Spacer(modifier = Modifier.height(28.dp))

            DecibelGauge(
                level = uiState.currentLevel,
                dbfs = uiState.currentDbfs,
                peakLevel = uiState.peakLevel,
                isListening = uiState.isListening
            )
            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(label = "Peak", value = "${uiState.peakLevel}", modifier = Modifier.weight(1f))
                StatCard(label = "Time", value = formatElapsed(uiState.elapsedSeconds), modifier = Modifier.weight(1f))
            }

            if (uiState.permission != MicPermissionState.GRANTED) {
                Spacer(modifier = Modifier.height(16.dp))
                MicrophoneOffCard(onShowPermissionDialog = onShowPermissionDialog)
            }

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(uiState.errorMessage, style = MaterialTheme.typography.bodyLarge, color = ErrorColor)
            }

            Spacer(modifier = Modifier.height(20.dp))
            ControlButtons(uiState = uiState, onToggleListening = onToggleListening, onFinish = onFinish)

            Spacer(modifier = Modifier.height(20.dp))
            PrivacyCard()
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

@Composable
private fun ScreamHeader(onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        //boton redondo de 48 dp (minimo tactil), color secondary como el resto de la navegacion
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SecondaryColor)
                .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextColor, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text("Scream tank", style = MaterialTheme.typography.headlineLarge, color = TextColor)
            Text("Let it out. Nobody is listening but you.", style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.7f))
        }
    }
}

//medidor circular: el arco muestra el volumen actual y la marca oscura el pico de la sesion
@Composable
private fun DecibelGauge(level: Int, dbfs: Double, peakLevel: Int, isListening: Boolean) {
    //animacion corta para que el arco no salte entre lecturas (llegan cada 50 ms)
    val animatedLevel by animateFloatAsState(
        targetValue = level / DecibelMeter.MAX_LEVEL.toFloat(),
        animationSpec = tween(durationMillis = 90),
        label = "screamLevel"
    )
    val arcColor = levelColor(level)
    val trackColor = TextColor.copy(alpha = 0.08f)
    val peakFraction = peakLevel / DecibelMeter.MAX_LEVEL.toFloat()

    Box(
        modifier = Modifier
            .size(240.dp)
            .clearAndSetSemantics { contentDescription = "Current level $level of 100, peak $peakLevel" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 20.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = GAUGE_START_ANGLE,
                sweepAngle = GAUGE_SWEEP_ANGLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            if (animatedLevel > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = GAUGE_START_ANGLE,
                    sweepAngle = GAUGE_SWEEP_ANGLE * animatedLevel,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            if (peakFraction > 0f) {
                drawArc(
                    color = TextColor,
                    startAngle = GAUGE_START_ANGLE + GAUGE_SWEEP_ANGLE * peakFraction - 1f,
                    sweepAngle = 2f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isListening) Icons.Filled.Mic else Icons.Filled.MicOff,
                contentDescription = null,
                tint = TextColor.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("$level", style = MaterialTheme.typography.headlineMedium, color = TextColor)
            Text(
                text = if (isListening) "${String.format(Locale.US, "%.1f", dbfs)} dB" else "Paused",
                style = MaterialTheme.typography.bodyLarge,
                color = TextColor.copy(alpha = 0.7f)
            )
        }
    }
}

//suave en secondary, fuerte en primary y muy fuerte en accent
//los umbrales de volumen son del modelo (DecibelMeter); aqui solo se elige el color
private fun levelColor(level: Int): Color = when (DecibelMeter.loudnessOf(level)) {
    Loudness.VERY_LOUD -> AccentColor
    Loudness.LOUD -> PrimaryColor
    Loudness.SOFT -> SecondaryColor
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ScreamCardFill)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = TextColor)
    }
}

@Composable
private fun MicrophoneOffCard(onShowPermissionDialog: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ScreamCardFill)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.MicOff, contentDescription = null, tint = TextColor, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "The meter needs the microphone.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextColor,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onShowPermissionDialog) {
            Text("Allow", style = MaterialTheme.typography.labelLarge, color = TextColor)
        }
    }
}

@Composable
private fun ControlButtons(uiState: ScreamTankUiState, onToggleListening: () -> Unit, onFinish: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = onFinish,
            enabled = uiState.hasStarted,
            modifier = Modifier
                .height(50.dp)
                .weight(1f),
            border = BorderStroke(1.2.dp, if (uiState.hasStarted) SecondaryColor else SecondaryColor.copy(alpha = 0.4f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TextColor,
                disabledContentColor = TextColor.copy(alpha = 0.4f)
            )
        ) {
            Text("Finish", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Button(
            onClick = onToggleListening,
            modifier = Modifier
                .height(50.dp)
                .weight(1.2f),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = TextColor)
        ) {
            Icon(
                imageVector = if (uiState.isListening) Icons.Filled.Pause else Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    uiState.isListening -> "Pause"
                    uiState.hasStarted -> "Resume"
                    else -> "Start"
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

//garantia de privacidad: se dice en la pantalla que no se graba nada
@Composable
private fun PrivacyCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ScreamCardFill)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = SecondaryColor, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text("Nothing is recorded", style = MaterialTheme.typography.headlineSmall, color = TextColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Ark only measures how loud you are, in memory, while this screen is open. No audio is saved on your phone or sent anywhere.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextColor.copy(alpha = 0.8f)
            )
        }
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

//mm:ss
private fun formatElapsed(seconds: Int): String {
    return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
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

@Preview(showBackground = true, name = "Scream tank - listening")
@Composable
fun ScreamTankListeningPreview() {
    AppTheme {
        ScreamTankScreen(
            uiState = ScreamTankUiState(
                permission = MicPermissionState.GRANTED,
                isListening = true,
                hasStarted = true,
                currentLevel = 72,
                currentDbfs = -16.8,
                peakLevel = 91,
                elapsedSeconds = 14
            ),
            onBack = {}, onToggleListening = {}, onFinish = {}
        )
    }
}
