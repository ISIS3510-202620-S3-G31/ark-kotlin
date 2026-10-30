package com.moviles.ark.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moviles.ark.ArkApplication
import com.moviles.ark.data.local.sensors.SpeechRecognitionState
import com.moviles.ark.data.local.sensors.SpeechToTextHelper
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.ErrorColor
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import kotlinx.coroutines.delay

//obtiene la instancia compartida de speechtotexthelper del appcontainer
@Composable
fun rememberSpeechToTextHelper(): SpeechToTextHelper {
    val context = LocalContext.current
    val app = context.applicationContext as? ArkApplication
    return remember(context) {
        app?.container?.speechToTextHelper ?: SpeechToTextHelper(context)
    }
}

//caja de entrada de texto moderna y reutilizable con limite de caracteres y microfono integrado
@Composable
fun ModernVoiceInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    maxLength: Int,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 80.dp,
    enabled: Boolean = true,
    speechToTextHelper: SpeechToTextHelper = rememberSpeechToTextHelper()
) {
    val remainingChars = (maxLength - value.length).coerceAtLeast(0)
    val isNearLimit = remainingChars <= (maxLength * 0.20f).toInt().coerceAtLeast(10)
    val isAtLimit = remainingChars == 0

    val counterColor = when {
        isAtLimit -> ErrorColor
        isNearLimit -> PrimaryColor
        else -> TextColor.copy(alpha = 0.60f)
    }

    val borderColor = when {
        isAtLimit -> ErrorColor.copy(alpha = 0.75f)
        isNearLimit -> PrimaryColor.copy(alpha = 0.60f)
        else -> SecondaryColor.copy(alpha = 0.35f)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TextColor.copy(alpha = 0.06f))
                .border(1.2.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FigtreeFontFamily,
                                fontSize = 14.sp
                            ),
                            color = TextColor.copy(alpha = 0.40f)
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { input ->
                            if (input.length <= maxLength) {
                                onValueChange(input)
                            }
                        },
                        enabled = enabled,
                        textStyle = TextStyle(
                            fontFamily = FigtreeFontFamily,
                            color = TextColor,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(PrimaryColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                //barra inferior de control: microfono a la izquierda y contador de caracteres a la derecha
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VoiceDictationComponent(
                        currentText = value,
                        onAppendText = { onValueChange(it.take(maxLength)) },
                        maxLength = maxLength,
                        speechToTextHelper = speechToTextHelper,
                        buttonSize = 34.dp,
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (isAtLimit) "0 remaining" else "$remainingChars remaining",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FigtreeFontFamily),
                        color = counterColor,
                        fontWeight = if (isNearLimit) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

//sobrecarga de dictado que anexa texto en vivo sin sobreescribir lo que ya estaba escrito en la caja de texto
@Composable
fun VoiceDictationComponent(
    currentText: String,
    onAppendText: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int = Int.MAX_VALUE,
    speechToTextHelper: SpeechToTextHelper = rememberSpeechToTextHelper(),
    buttonSize: Dp = 38.dp,
    enabled: Boolean = true
) {
    var baseTextSnapshot by remember { mutableStateOf("") }
    val state by speechToTextHelper.state.collectAsStateWithLifecycle()

    //al entrar en escucha captura el texto actual como base inmutable para esta sesion de dictado
    LaunchedEffect(state) {
        if (state == SpeechRecognitionState.LISTENING) {
            baseTextSnapshot = currentText.trimEnd()
        }
    }

    VoiceDictationComponent(
        onTranscription = { spokenText ->
            val cleanSpoken = spokenText.trim()
            if (cleanSpoken.isNotEmpty()) {
                val combined = if (baseTextSnapshot.isBlank()) {
                    cleanSpoken
                } else {
                    "$baseTextSnapshot $cleanSpoken"
                }
                onAppendText(combined.take(maxLength))
            }
        },
        modifier = modifier,
        speechToTextHelper = speechToTextHelper,
        buttonSize = buttonSize,
        enabled = enabled
    )
}

//componente de dictado por voz para entradas de texto
//tocar el microfono empieza a escuchar y sigue escuchando hasta tocar parar; al lado dice en que estado esta
@Composable
fun VoiceDictationComponent(
    onTranscription: (String) -> Unit,
    modifier: Modifier = Modifier,
    speechToTextHelper: SpeechToTextHelper = rememberSpeechToTextHelper(),
    buttonSize: Dp = 38.dp,
    enabled: Boolean = true
) {
    val state by speechToTextHelper.state.collectAsStateWithLifecycle()
    val transcription by speechToTextHelper.transcription.collectAsStateWithLifecycle()
    val soundLevel by speechToTextHelper.soundLevel.collectAsStateWithLifecycle()
    val errorMessage by speechToTextHelper.errorMessage.collectAsStateWithLifecycle()

    //identifica a esta caja de texto ante el helper compartido: solo recibe lo que ella misma dicto
    val sessionOwner = remember { Any() }

    //aviso corto de una linea junto al microfono; se quita solo para no tapar nada
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_VISIBLE_MILLIS)
            notice = null
        }
    }

    //permiso de microfono pedido de forma nativa en compose
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notice = if (isGranted) startListeningWithCheck(speechToTextHelper, sessionOwner) else SpeechToTextHelper.MIC_PERMISSION_NOTICE
    }

    //emite el texto en vivo que viene por los flujos de memoria ram (stateflow)
    //lo dictado en otra pantalla (otro feedback) nunca llega a esta caja
    LaunchedEffect(transcription) {
        if (transcription.isNotBlank() && speechToTextHelper.isOwnedBy(sessionOwner)) {
            onTranscription(transcription)
        }
    }

    //si el dictado falla, el helper ya trae el aviso corto
    LaunchedEffect(errorMessage) {
        if (speechToTextHelper.isOwnedBy(sessionOwner)) errorMessage?.let { notice = it }
    }

    //limpia recursos al salir de la pantalla
    //al salir de la pantalla siempre se apaga el microfono de esta caja; antes dependia de un estado que podia
    //estar desactualizado y el dictado seguia escuchando y llenaba el siguiente feedback
    DisposableEffect(speechToTextHelper) {
        onDispose { speechToTextHelper.release(sessionOwner) }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VoiceDictationButton(
            state = state,
            soundLevel = soundLevel,
            size = buttonSize,
            enabled = enabled,
            onClick = {
                when {
                    state == SpeechRecognitionState.LISTENING -> speechToTextHelper.stopListening()
                    !speechToTextHelper.hasPermission() -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    else -> notice = startListeningWithCheck(speechToTextHelper, sessionOwner)
                }
            }
        )

        when {
            state == SpeechRecognitionState.LISTENING -> {
                RealtimeAudioVisualizer(soundLevel = soundLevel)
                DictationStatusText(text = "Listening · tap to stop", color = PrimaryColor)
            }
            state == SpeechRecognitionState.PROCESSING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = SecondaryColor,
                    strokeWidth = 1.5.dp
                )
                DictationStatusText(text = "Processing...", color = SecondaryColor)
            }
            notice != null -> DictationStatusText(text = notice.orEmpty(), color = TextColor.copy(alpha = 0.65f))
        }
    }
}

//texto de estado del dictado en una sola linea
@Composable
private fun DictationStatusText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FigtreeFontFamily, fontSize = 12.sp),
        color = color,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

//visualizador de barras de audio en tiempo real reactivo al nivel de sonido (RMS)
@Composable
fun RealtimeAudioVisualizer(
    soundLevel: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "audioWave")
    val waveAnim by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(350),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveHeight"
    )

    // Combina una animacion organica base con la reactividad real del microfono
    val factor = (soundLevel * 0.70f + waveAnim * 0.30f).coerceIn(0.1f, 1f)

    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val h1 = (4.dp + (10.dp * factor * 0.7f)).coerceIn(3.dp, 14.dp)
        val h2 = (6.dp + (14.dp * factor * 1.2f)).coerceIn(4.dp, 18.dp)
        val h3 = (5.dp + (12.dp * factor * 0.9f)).coerceIn(4.dp, 16.dp)
        val h4 = (4.dp + (8.dp * factor * 0.6f)).coerceIn(3.dp, 12.dp)

        Box(modifier = Modifier.width(2.5.dp).height(h1).clip(RoundedCornerShape(1.5.dp)).background(PrimaryColor))
        Box(modifier = Modifier.width(2.5.dp).height(h2).clip(RoundedCornerShape(1.5.dp)).background(PrimaryColor))
        Box(modifier = Modifier.width(2.5.dp).height(h3).clip(RoundedCornerShape(1.5.dp)).background(PrimaryColor))
        Box(modifier = Modifier.width(2.5.dp).height(h4).clip(RoundedCornerShape(1.5.dp)).background(PrimaryColor))
    }
}

//boton circular con animacion de pulso al escuchar
@Composable
fun VoiceDictationButton(
    state: SpeechRecognitionState,
    soundLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
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

    val iconSize = (size.value * 0.55f).dp

    Box(
        modifier = modifier
            .size(size)
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
                modifier = Modifier.size(iconSize),
                color = SecondaryColor,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop voice dictation" else "Dictate note with voice",
                tint = iconColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

//verifica si el celular tiene reconocimiento disponible antes de activar el microfono; devuelve el aviso si no se pudo
private fun startListeningWithCheck(helper: SpeechToTextHelper, owner: Any): String? {
    if (!helper.isRecognitionAvailable()) return SpeechToTextHelper.VOICE_UNAVAILABLE_NOTICE
    helper.startListening(owner)
    return null
}

private const val NOTICE_VISIBLE_MILLIS = 4_000L

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
