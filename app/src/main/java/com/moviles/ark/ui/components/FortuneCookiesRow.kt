package com.moviles.ark.ui.components

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.view.SoundEffectConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor
import java.util.Random
import kotlin.math.exp

//reproductor de sonido sintetizado para el crujido fisico de la galleta
object CookieCrunchSoundPlayer {
    fun playCrunch() {
        try {
            val sampleRate = 22050
            val durationMs = 170
            val numSamples = (sampleRate * durationMs / 1000)
            val buffer = ShortArray(numSamples)
            val random = Random()
            for (i in 0 until numSamples) {
                val t = i.toDouble() / numSamples
                val envelope = exp(-t * 11.0)
                val noise = random.nextDouble() * 2.0 - 1.0
                val snap = if (i < 400) (random.nextDouble() * 2.0 - 1.0) * 1.7 else 0.0
                val sample = (noise * 0.45 + snap * 0.55) * envelope
                buffer[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.size * 2,
                AudioTrack.MODE_STATIC
            )
            track.write(buffer, 0, buffer.size)
            track.play()
        } catch (_: Exception) {
            //si no se puede inicializar el reproductor se omite silenciosamente
        }
    }
}

//fila con las 3 galletas renderizadas con volumen 3d sombreado y animacion de quiebre
@Composable
fun FortuneCookiesRow(
    crackingIndex: Int,
    openedCookies: Set<Int>,
    onCrackCookie: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in 0..2) {
            val isCracking = crackingIndex == index
            val isOpened = openedCookies.contains(index)

            val crackProgress by animateFloatAsState(
                targetValue = if (isCracking || isOpened) 1f else 0f,
                animationSpec = if (isCracking) spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium) else tween(300),
                label = "crackProgress"
            )

            val scaleBounce by animateFloatAsState(
                targetValue = if (isCracking) 1.08f else 1.0f,
                animationSpec = tween(durationMillis = 200),
                label = "scaleBounce"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!isOpened) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            try {
                                view.playSoundEffect(SoundEffectConstants.CLICK)
                            } catch (_: Exception) {}
                            CookieCrunchSoundPlayer.playCrunch()
                            onCrackCookie(index)
                        }
                    }
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .graphicsLayer {
                            scaleX = scaleBounce
                            scaleY = scaleBounce
                        },
                    contentAlignment = Alignment.Center
                ) {
                    //sombra inferior suave 3d
                    Canvas(modifier = Modifier.size(90.dp)) {
                        drawOval(
                            color = Color(0x333A1F05),
                            topLeft = Offset(size.width * 0.15f, size.height * 0.78f),
                            size = Size(size.width * 0.7f, size.height * 0.18f)
                        )
                    }

                    //papelito de la fortuna que emerge hacia arriba al romperse
                    if (isCracking || isOpened) {
                        val paperTranslation = (1f - crackProgress) * 20f - crackProgress * 18f
                        Canvas(
                            modifier = Modifier
                                .size(50.dp, 28.dp)
                                .graphicsLayer {
                                    translationY = paperTranslation
                                    rotationZ = (index - 1) * 6f
                                }
                        ) {
                            //cuerpo del papel blanco con sombreado sutil
                            drawRoundRect(
                                color = Color.White,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )
                            drawRoundRect(
                                color = Color(0xFFDDD2C4),
                                style = Stroke(width = 1.5f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )
                            //lineas rojas y doradas simulando el texto de la fortuna
                            drawLine(
                                color = Color(0xFFE53935),
                                start = Offset(8f, 10f),
                                end = Offset(size.width - 8f, 10f),
                                strokeWidth = 2.5f
                            )
                            drawLine(
                                color = Color(0xFFD48B28),
                                start = Offset(12f, 18f),
                                end = Offset(size.width - 12f, 18f),
                                strokeWidth = 1.8f
                            )
                        }
                    }

                    //mitad izquierda de la galleta con perspectiva 3d
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer {
                                cameraDistance = 14f * density
                                rotationY = -34f * crackProgress
                                rotationZ = -14f * crackProgress
                                translationX = -18f * crackProgress * density
                                translationY = 4f * crackProgress * density
                            }
                    ) {
                        Canvas(modifier = Modifier.size(100.dp)) {
                            drawLeftCookieHalf(size, isOpened)
                        }
                    }

                    //mitad derecha de la galleta con perspectiva 3d
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer {
                                cameraDistance = 14f * density
                                rotationY = 34f * crackProgress
                                rotationZ = 14f * crackProgress
                                translationX = 18f * crackProgress * density
                                translationY = 4f * crackProgress * density
                            }
                    ) {
                        Canvas(modifier = Modifier.size(100.dp)) {
                            drawRightCookieHalf(size, isOpened)
                        }
                    }

                    //particulas y migajas al romperse
                    if (isCracking) {
                        Canvas(modifier = Modifier.size(100.dp)) {
                            val crumbColor = Color(0xFFE89E3A)
                            drawCircle(crumbColor, radius = 3.5f, center = Offset(size.width * 0.45f - 15f * crackProgress, size.height * 0.5f - 10f * crackProgress))
                            drawCircle(crumbColor, radius = 2.5f, center = Offset(size.width * 0.55f + 14f * crackProgress, size.height * 0.52f - 12f * crackProgress))
                            drawCircle(Color(0xFFBA6910), radius = 3f, center = Offset(size.width * 0.5f, size.height * 0.65f + 10f * crackProgress))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isOpened) "Cracked" else "Cookie ${index + 1}",
                    fontSize = 12.sp,
                    fontWeight = if (isCracking) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isOpened) Color.Gray else TextColor
                )
            }
        }
    }
}

//dibuja el ala izquierda de la galleta con gradiente de horneado y volumen curvo
private fun DrawScope.drawLeftCookieHalf(size: Size, isOpened: Boolean) {
    val w = size.width
    val h = size.height

    val baseGradient = Brush.radialGradient(
        colors = if (isOpened) listOf(Color(0xFFE8B77D), Color(0xFFC78C4D), Color(0xFF985A22)) else listOf(Color(0xFFFFD187), Color(0xFFEAA03B), Color(0xFFB55D0E)),
        center = Offset(w * 0.35f, h * 0.4f),
        radius = w * 0.5f
    )

    val path = Path().apply {
        //punta superior del pliegue
        moveTo(w * 0.50f, h * 0.22f)
        //curva del lomo exterior izquierdo hacia la punta
        cubicTo(w * 0.28f, h * 0.20f, w * 0.08f, h * 0.42f, w * 0.14f, h * 0.72f)
        //borde redondeado del ala
        cubicTo(w * 0.20f, h * 0.84f, w * 0.34f, h * 0.80f, w * 0.42f, h * 0.68f)
        //retorno por el pliegue central interior
        cubicTo(w * 0.48f, h * 0.58f, w * 0.50f, h * 0.40f, w * 0.50f, h * 0.22f)
        close()
    }

    drawPath(path = path, brush = baseGradient, style = Fill)

    //sombreado tostado en el borde exterior
    val rimShadow = Path().apply {
        moveTo(w * 0.50f, h * 0.22f)
        cubicTo(w * 0.28f, h * 0.20f, w * 0.08f, h * 0.42f, w * 0.14f, h * 0.72f)
        cubicTo(w * 0.20f, h * 0.84f, w * 0.34f, h * 0.80f, w * 0.42f, h * 0.68f)
    }
    drawPath(path = rimShadow, color = Color(0x66723204), style = Stroke(width = 3.5f))

    //brillo especular en la cresta del lomo
    val highlightPath = Path().apply {
        moveTo(w * 0.38f, h * 0.28f)
        cubicTo(w * 0.25f, h * 0.34f, w * 0.18f, h * 0.48f, w * 0.22f, h * 0.64f)
    }
    drawPath(path = highlightPath, color = Color(0x55FFFFFF), style = Stroke(width = 3f))
}

//dibuja el ala derecha de la galleta con gradiente de horneado y volumen curvo
private fun DrawScope.drawRightCookieHalf(size: Size, isOpened: Boolean) {
    val w = size.width
    val h = size.height

    val baseGradient = Brush.radialGradient(
        colors = if (isOpened) listOf(Color(0xFFE8B77D), Color(0xFFC78C4D), Color(0xFF985A22)) else listOf(Color(0xFFFFD187), Color(0xFFEAA03B), Color(0xFFB55D0E)),
        center = Offset(w * 0.65f, h * 0.4f),
        radius = w * 0.5f
    )

    val path = Path().apply {
        //punta superior del pliegue
        moveTo(w * 0.50f, h * 0.22f)
        //curva del lomo exterior derecho hacia la punta
        cubicTo(w * 0.72f, h * 0.20f, w * 0.92f, h * 0.42f, w * 0.86f, h * 0.72f)
        //borde redondeado del ala
        cubicTo(w * 0.80f, h * 0.84f, w * 0.66f, h * 0.80f, w * 0.58f, h * 0.68f)
        //retorno por el pliegue central interior
        cubicTo(w * 0.52f, h * 0.58f, w * 0.50f, h * 0.40f, w * 0.50f, h * 0.22f)
        close()
    }

    drawPath(path = path, brush = baseGradient, style = Fill)

    //sombreado tostado en el borde exterior
    val rimShadow = Path().apply {
        moveTo(w * 0.50f, h * 0.22f)
        cubicTo(w * 0.72f, h * 0.20f, w * 0.92f, h * 0.42f, w * 0.86f, h * 0.72f)
        cubicTo(w * 0.80f, h * 0.84f, w * 0.66f, h * 0.80f, w * 0.58f, h * 0.68f)
    }
    drawPath(path = rimShadow, color = Color(0x66723204), style = Stroke(width = 3.5f))

    //brillo especular en la cresta del lomo
    val highlightPath = Path().apply {
        moveTo(w * 0.62f, h * 0.28f)
        cubicTo(w * 0.75f, h * 0.34f, w * 0.82f, h * 0.48f, w * 0.78f, h * 0.64f)
    }
    drawPath(path = highlightPath, color = Color(0x55FFFFFF), style = Stroke(width = 3f))
}
