package com.moviles.ark.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moviles.ark.ArkApplication
import com.moviles.ark.data.local.sensors.SpeechRecognitionState
import com.moviles.ark.data.local.sensors.SpeechToTextHelper
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor

//obtiene la instancia compartida de speechtotexthelper del appcontainer
@Composable
fun rememberSpeechToTextHelper(): SpeechToTextHelper {
    val context = LocalContext.current
    val app = context.applicationContext as? ArkApplication
    return remember(context) {
        app?.container?.speechToTextHelper ?: SpeechToTextHelper(context)
    }
}

//componente de dictado por voz para entradas de texto (ej. notas y pies de foto)
//aplica tecnicas del curso: multithreading en ram y fallback graceful hacia teclado
@Composable
fun VoiceDictationComponent(
    onTranscription: (String) -> Unit,
    modifier: Modifier = Modifier,
    speechToTextHelper: SpeechToTextHelper = rememberSpeechToTextHelper(),
    enabled: Boolean = true,
    onFallback: (String) -> Unit = {}
) {
    val state by speechToTextHelper.state.collectAsStateWithLifecycle()
    val transcription by speechToTextHelper.transcription.collectAsStateWithLifecycle()
    val soundLevel by speechToTextHelper.soundLevel.collectAsStateWithLifecycle()
    val errorMessage by speechToTextHelper.errorMessage.collectAsStateWithLifecycle()

    var fallbackNotice by remember { mutableStateOf<String?>(null) }

    //permiso de microfono pedido de forma nativa en compose
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fallbackNotice = null
            startListeningWithCheck(speechToTextHelper) { notice ->
                fallbackNotice = notice
                onFallback(notice)
            }
        } else {
            val notice = "Microphone denied. Please use the keyboard."
            fallbackNotice = notice
            onFallback(notice)
        }
    }

    //emite el texto en vivo que viene por los flujos de memoria ram (stateflow)
    LaunchedEffect(transcription) {
        if (transcription.isNotBlank()) {
            onTranscription(transcription)
        }
    }

    //si ocurre un error de hardware o audio, avisa para usar el teclado
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            val notice = "Voice dictation stopped. Please use the keyboard."
            fallbackNotice = notice
            onFallback(notice)
        }
    }

    //limpia recursos al salir de la pantalla
    DisposableEffect(speechToTextHelper) {
        onDispose {
            if (state == SpeechRecognitionState.LISTENING) {
                speechToTextHelper.cancel()
            }
        }
    }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoiceDictationButton(
                state = state,
                soundLevel = soundLevel,
                enabled = enabled,
                onClick = {
                    if (state == SpeechRecognitionState.LISTENING) {
                        speechToTextHelper.stopListening()
                    } else {
                        if (!speechToTextHelper.hasPermission()) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            startListeningWithCheck(speechToTextHelper) { notice ->
                                fallbackNotice = notice
                                onFallback(notice)
                            }
                        }
                    }
                }
            )

            if (state == SpeechRecognitionState.LISTENING) {
                Text(
                    text = "Listening...",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FigtreeFontFamily),
                    color = AccentColor,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (state == SpeechRecognitionState.PROCESSING) {
                Text(
                    text = "Processing...",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FigtreeFontFamily),
                    color = SecondaryColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        //tarjeta de respaldo (fallback) corta y clara si no hay permiso o falta el modelo offline
        AnimatedVisibility(visible = fallbackNotice != null) {
            fallbackNotice?.let { notice ->
                Spacer(modifier = Modifier.height(6.dp))
                VoiceFallbackNotice(
                    message = notice,
                    onDismiss = { fallbackNotice = null }
                )
            }
        }
    }
}

//boton circular con animacion de pulso al escuchar
@Composable
fun VoiceDictationButton(
    state: SpeechRecognitionState,
    soundLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isListening = state == SpeechRecognitionState.LISTENING
    val isProcessing = state == SpeechRecognitionState.PROCESSING

    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.20f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val waveScale = if (isListening) (1f + (soundLevel * 0.25f)).coerceIn(1f, 1.35f) else 1f

    val backgroundColor = when {
        isListening -> AccentColor.copy(alpha = 0.15f)
        isProcessing -> SecondaryColor.copy(alpha = 0.15f)
        else -> SecondaryColor.copy(alpha = 0.10f)
    }

    val iconColor = when {
        isListening -> AccentColor
        isProcessing -> SecondaryColor
        else -> TextColor.copy(alpha = 0.70f)
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(if (isListening) waveScale * pulse else 1f)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = if (isListening) "Stop voice dictation" else "Start voice dictation",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = SecondaryColor,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop voice dictation" else "Dictate note with voice",
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

//aviso corto de respaldo cuando se debe usar el teclado
@Composable
fun VoiceFallbackNotice(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, PrimaryColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
        color = Color.White.copy(alpha = 0.95f),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = "Keyboard fallback",
                tint = PrimaryColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FigtreeFontFamily, fontSize = 12.sp),
                color = TextColor.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss notice",
                    tint = TextColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

//verifica si el celular tiene motor local antes de activar el microfono
private fun startListeningWithCheck(
    helper: SpeechToTextHelper,
    onFallback: (String) -> Unit
) {
    if (!helper.isRecognitionAvailable()) {
        onFallback("Voice recognition unavailable. Please use the keyboard.")
        return
    }

    if (!helper.isOnDeviceRecognitionAvailable()) {
        onFallback("Offline voice model missing. Please use the keyboard.")
        return
    }

    helper.startListening()
}

@Preview(showBackground = true, name = "Voice Dictation - Idle")
@Composable
fun VoiceDictationButtonIdlePreview() {
    AppTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            VoiceDictationButton(
                state = SpeechRecognitionState.IDLE,
                soundLevel = 0f,
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Voice Dictation - Fallback Notice")
@Composable
fun VoiceFallbackNoticePreview() {
    AppTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            VoiceFallbackNotice(
                message = "Microphone denied. Please use the keyboard.",
                onDismiss = {}
            )
        }
    }
}
