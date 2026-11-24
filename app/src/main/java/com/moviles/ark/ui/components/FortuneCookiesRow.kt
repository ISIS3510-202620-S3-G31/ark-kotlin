package com.moviles.ark.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import android.view.SoundEffectConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor

//fila que dibuja las 3 galletas rompibles con animacion de quiebre y efectos
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
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in 0..2) {
            val isCracking = crackingIndex == index
            val isOpened = openedCookies.contains(index)

            val crackRotation by animateFloatAsState(
                targetValue = if (isCracking || isOpened) 22f else 0f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "crackRotation"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        try {
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                            ToneGenerator(AudioManager.STREAM_MUSIC, 90).startTone(ToneGenerator.TONE_PROP_ACK, 70)
                        } catch (_: Exception) {
                            //si el dispositivo no tiene altavoz activo se omite el tono
                        }
                        onCrackCookie(index)
                    }
                )
            ) {
                Box(
                    modifier = Modifier.size(90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(80.dp)) {
                        val cookieColor = if (isOpened) Color(0xFFD4A373).copy(alpha = 0.5f) else Color(0xFFE9963F)
                        val shadowColor = Color(0xFFC07020)

                        //mitad izquierda de la galleta
                        rotate(degrees = -crackRotation, pivot = Offset(size.width * 0.2f, size.height * 0.5f)) {
                            val leftPath = Path().apply {
                                moveTo(size.width * 0.5f, size.height * 0.15f)
                                cubicTo(
                                    size.width * 0.2f, size.height * 0.15f,
                                    size.width * 0.05f, size.height * 0.45f,
                                    size.width * 0.15f, size.height * 0.85f
                                )
                                cubicTo(
                                    size.width * 0.3f, size.height * 0.75f,
                                    size.width * 0.45f, size.height * 0.65f,
                                    size.width * 0.5f, size.height * 0.5f
                                )
                                close()
                            }
                            drawPath(leftPath, color = cookieColor)
                            drawPath(leftPath, color = shadowColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                        }

                        //mitad derecha de la galleta
                        rotate(degrees = crackRotation, pivot = Offset(size.width * 0.8f, size.height * 0.5f)) {
                            val rightPath = Path().apply {
                                moveTo(size.width * 0.5f, size.height * 0.15f)
                                cubicTo(
                                    size.width * 0.8f, size.height * 0.15f,
                                    size.width * 0.95f, size.height * 0.45f,
                                    size.width * 0.85f, size.height * 0.85f
                                )
                                cubicTo(
                                    size.width * 0.7f, size.height * 0.75f,
                                    size.width * 0.55f, size.height * 0.65f,
                                    size.width * 0.5f, size.height * 0.5f
                                )
                                close()
                            }
                            drawPath(rightPath, color = cookieColor)
                            drawPath(rightPath, color = shadowColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                        }

                        //papelito de fortuna que asoma si se esta quebrando
                        if (isCracking || isOpened) {
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(size.width * 0.35f, size.height * 0.35f),
                                size = Size(size.width * 0.3f, size.height * 0.18f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )
                        }
                    }
                }

                Text(
                    text = if (isOpened) "Opened" else "Cookie ${index + 1}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isOpened) Color.Gray else TextColor
                )
            }
        }
    }
}
