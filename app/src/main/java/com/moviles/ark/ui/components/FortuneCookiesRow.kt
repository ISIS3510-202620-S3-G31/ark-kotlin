package com.moviles.ark.ui.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.FortunePhase
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

//colores de la galleta sacados de la paleta, del fondo al naranja y al cafe del texto
private val CrustLight = lerp(BackgroundColor, PrimaryColor, 0.35f)
private val CrustMid = lerp(BackgroundColor, PrimaryColor, 0.75f)
private val CrustDark = lerp(PrimaryColor, TextColor, 0.45f)
private val CrustShadow = lerp(PrimaryColor, TextColor, 0.7f)

//sonidos hechos con numeros, no necesitan archivos de audio
object CookieCrunchSoundPlayer {
    //chasquido seco con un golpe grave, como una galleta que se parte
    fun playCrunch() {
        val sampleRate = 22050
        val samples = sampleRate * 260 / 1000
        val random = Random()
        val buffer = ShortArray(samples)
        for (i in 0 until samples) {
            val t = i.toDouble() / sampleRate
            val snap = if (i < 420 || i in 1500..1800) (random.nextDouble() * 2.0 - 1.0) * exp(-t * 30.0) else 0.0
            val thud = sin(2.0 * PI * 90.0 * t) * exp(-t * 18.0) * 0.6
            val crumbs = (random.nextDouble() * 2.0 - 1.0) * exp(-t * 12.0) * 0.25
            buffer[i] = ((snap + thud + crumbs).coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.9).toInt().toShort()
        }
        play(buffer, sampleRate)
    }

    //roce suave de papel cuando sale el papelito
    fun playRustle() {
        val sampleRate = 22050
        val samples = sampleRate * 380 / 1000
        val random = Random()
        val buffer = ShortArray(samples)
        var last = 0.0
        for (i in 0 until samples) {
            val t = i.toDouble() / samples
            //ruido suavizado para que suene a papel y no a estatica
            last = last * 0.7 + (random.nextDouble() * 2.0 - 1.0) * 0.3
            val envelope = sin(PI * t) * 0.35
            buffer[i] = ((last * envelope).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        play(buffer, sampleRate)
    }

    //reproduce el sonido una vez y libera el reproductor al terminar
    private fun play(buffer: ShortArray, sampleRate: Int) {
        try {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val format = AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            val track = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format).setBufferSizeInBytes(buffer.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            track.write(buffer, 0, buffer.size)
            track.setNotificationMarkerPosition(buffer.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { t.release() }
                override fun onPeriodicNotification(t: AudioTrack) {}
            })
            track.play()
        } catch (_: Exception) {
            //si el telefono no deja reproducir se sigue sin sonido
        }
    }
}

//las 3 galletas sobre la bandeja de vidrio
//la elegida llega al centro, se aprieta, le sale una grieta, se parte en dos y saltan migajas
@Composable
fun FortuneCookiesRow(phase: FortunePhase, chosenIndex: Int, hasChances: Boolean, onCrackCookie: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val localDensity = LocalDensity.current
    val spacing = with(localDensity) { 104.dp.toPx() }
    val restY = with(localDensity) { 18.dp.toPx() }
    val liftY = with(localDensity) { (-4).dp.toPx() }
    val spread = with(localDensity) { 26.dp.toPx() }
    val drop = with(localDensity) { 46.dp.toPx() }
    val sink = with(localDensity) { 8.dp.toPx() }

    //avance de cada parte de la animacion, de 0 a 1
    val travel = remember { Animatable(0f) }
    val squeeze = remember { Animatable(0f) }
    val crackLine = remember { Animatable(0f) }
    val split = remember { Animatable(0f) }

    LaunchedEffect(phase) {
        if (phase == FortunePhase.CHOOSING) {
            travel.snapTo(0f)
            squeeze.snapTo(0f)
            crackLine.snapTo(0f)
            split.snapTo(0f)
        }
        if (phase == FortunePhase.OPENING) {
            //llega al centro
            travel.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            //se aprieta dos veces mientras la grieta baja por el centro
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            launch { crackLine.animateTo(1f, tween(620, easing = FastOutSlowInEasing)) }
            squeeze.animateTo(1f, tween(620, easing = LinearEasing))
            //se parte
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            CookieCrunchSoundPlayer.playCrunch()
            split.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
        }
        if (phase == FortunePhase.REVEALED) {
            travel.snapTo(1f)
            squeeze.snapTo(1f)
            crackLine.snapTo(1f)
            if (split.value < 1f) split.animateTo(1f, tween(200))
        }
    }

    //el escenario se achica cuando sale el papelito
    val targetHeight = if (phase == FortunePhase.CHOOSING) 230.dp else if (phase == FortunePhase.OPENING) 280.dp else 150.dp
    val stageHeight by animateDpAsState(targetHeight, tween(600, easing = FastOutSlowInEasing), label = "stageHeight")
    val focusScale by animateFloatAsState(if (phase == FortunePhase.REVEALED) 1.25f else 2f, tween(600, easing = FastOutSlowInEasing), label = "focusScale")
    val trayAlpha by animateFloatAsState(if (phase == FortunePhase.CHOOSING) 1f else 0f, tween(400), label = "trayAlpha")

    Box(modifier = modifier.fillMaxWidth().height(stageHeight), contentAlignment = Alignment.Center) {
        //bandeja de vidrio donde descansan las galletas
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).fillMaxWidth(0.9f).height(64.dp).graphicsLayer { alpha = trayAlpha }.liquidGlass(RoundedCornerShape(percent = 50)))

        for (index in 0..2) {
            val isChosen = index == chosenIndex
            val slotX = (index - 1) * spacing
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val pressScale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
            val canOpen = phase == FortunePhase.CHOOSING && hasChances
            val cookieLayer = Modifier.graphicsLayer {
                val t = travel.value
                if (isChosen) {
                    //la elegida llega al centro, crece y se inclina un poco hacia la persona
                    val s = 1f + (focusScale - 1f) * t
                    //apreton, se aplasta y se estira dos veces antes de partirse
                    val pulse = sin(squeeze.value * PI * 4).toFloat() * 0.05f * (1f - split.value)
                    translationX = slotX * (1f - t)
                    translationY = restY + (liftY - restY) * t
                    scaleX = s * (1f + pulse)
                    scaleY = s * (1f - pulse)
                    rotationX = 12f * t * (1f - split.value)
                    cameraDistance = 12f * density
                } else {
                    //las otras bajan y se desvanecen
                    translationX = slotX
                    translationY = restY + t * drop
                    scaleX = pressScale * (1f - 0.25f * t)
                    scaleY = pressScale * (1f - 0.25f * t)
                    alpha = (if (hasChances) 1f else 0.4f) * (1f - t)
                }
            }
            Box(modifier = Modifier.size(96.dp).then(cookieLayer).clickable(interactionSource = interaction, indication = null, enabled = canOpen, role = Role.Button, onClickLabel = "Open cookie ${index + 1}", onClick = { onCrackCookie(index) }), contentAlignment = Alignment.Center) {
                val s = if (isChosen) split.value else 0f
                //sombra suave debajo
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawOval(color = TextColor.copy(alpha = 0.18f * (1f - s * 0.6f)), topLeft = Offset(size.width * 0.12f, size.height * 0.80f), size = Size(size.width * 0.76f, size.height * 0.1f))
                }
                if (s > 0f) {
                    //mitad izquierda, se abre hacia la izquierda girando en 3d
                    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { halfLayer(s, -1f, spread, sink) }) { drawHalf(true) }
                    //mitad derecha, igual hacia el otro lado
                    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { halfLayer(s, 1f, spread, sink) }) { drawHalf(false) }
                } else {
                    //galleta entera mientras no se parte
                    Canvas(modifier = Modifier.fillMaxSize()) { drawWholeCookie() }
                }
                //grieta que baja por el centro antes de partirse
                if (isChosen && crackLine.value > 0f && s == 0f) {
                    Canvas(modifier = Modifier.fillMaxSize()) { drawCrackLine(crackLine.value) }
                }
                //migajas que salen volando al partirse
                if (isChosen && s > 0f && s < 1f) {
                    Canvas(modifier = Modifier.fillMaxSize()) { drawCrumbs(s) }
                }
            }
        }
    }
}

//mueve una mitad, side es -1 para la izquierda y 1 para la derecha
private fun GraphicsLayerScope.halfLayer(s: Float, side: Float, spread: Float, sink: Float) {
    cameraDistance = 10f * density
    transformOrigin = TransformOrigin(0.5f + side * 0.15f, 0.6f)
    translationX = side * spread * s
    translationY = sink * s
    rotationZ = side * 22f * s
    rotationY = -side * 35f * s
}

//puntos de la grieta en zigzag, del lomo hasta la punta de la abertura
private fun crackPoints(w: Float, h: Float): List<Offset> {
    return listOf(Offset(w * 0.50f, h * 0.18f), Offset(w * 0.47f, h * 0.25f), Offset(w * 0.53f, h * 0.31f), Offset(w * 0.48f, h * 0.37f), Offset(w * 0.52f, h * 0.43f), Offset(w * 0.50f, h * 0.48f))
}

//camino de la grieta
private fun crackPath(w: Float, h: Float): Path {
    val points = crackPoints(w, h)
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (p in points.drop(1)) path.lineTo(p.x, p.y)
    return path
}

//dibuja la grieta hasta donde va, usando solo un pedazo del camino
private fun DrawScope.drawCrackLine(progress: Float) {
    val measure = PathMeasure()
    measure.setPath(crackPath(size.width, size.height), false)
    val piece = Path()
    measure.getSegment(0f, measure.length * progress, piece, true)
    drawPath(path = piece, color = CrustShadow, style = Stroke(width = size.width * 0.018f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

//zona de un lado de la grieta, sirve para recortar la galleta en dos
private fun halfClip(w: Float, h: Float, left: Boolean): Path {
    val path = Path()
    val edge = if (left) -w else w * 2f
    path.moveTo(w * 0.5f, -h)
    for (p in crackPoints(w, h)) path.lineTo(p.x, p.y)
    path.lineTo(w * 0.5f, h * 2f)
    path.lineTo(edge, h * 2f)
    path.lineTo(edge, -h)
    path.close()
    return path
}

//dibuja una mitad: la galleta completa recortada por la grieta y el borde roto un poco mas oscuro
private fun DrawScope.drawHalf(left: Boolean) {
    clipPath(halfClip(size.width, size.height, left)) {
        drawWholeCookie()
        drawPath(path = crackPath(size.width, size.height), color = CrustShadow.copy(alpha = 0.7f), style = Stroke(width = size.width * 0.05f, join = StrokeJoin.Round))
    }
}

//silueta de la galleta vista de frente, lomo redondo arriba, dos alas que bajan y la abertura en el centro
private fun cookiePath(w: Float, h: Float): Path {
    val p = Path()
    p.moveTo(w * 0.03f, h * 0.66f)
    p.cubicTo(w * 0.03f, h * 0.36f, w * 0.26f, h * 0.18f, w * 0.50f, h * 0.18f)
    p.cubicTo(w * 0.74f, h * 0.18f, w * 0.97f, h * 0.36f, w * 0.97f, h * 0.66f)
    p.cubicTo(w * 0.97f, h * 0.80f, w * 0.90f, h * 0.88f, w * 0.80f, h * 0.88f)
    p.cubicTo(w * 0.70f, h * 0.88f, w * 0.64f, h * 0.80f, w * 0.60f, h * 0.70f)
    p.cubicTo(w * 0.57f, h * 0.62f, w * 0.54f, h * 0.52f, w * 0.50f, h * 0.48f)
    p.cubicTo(w * 0.46f, h * 0.52f, w * 0.43f, h * 0.62f, w * 0.40f, h * 0.70f)
    p.cubicTo(w * 0.36f, h * 0.80f, w * 0.30f, h * 0.88f, w * 0.20f, h * 0.88f)
    p.cubicTo(w * 0.10f, h * 0.88f, w * 0.03f, h * 0.80f, w * 0.03f, h * 0.66f)
    p.close()
    return p
}

//hueco de adentro que se ve por la abertura de abajo
private fun cavityPath(w: Float, h: Float): Path {
    val p = Path()
    p.moveTo(w * 0.50f, h * 0.48f)
    p.cubicTo(w * 0.46f, h * 0.52f, w * 0.43f, h * 0.62f, w * 0.40f, h * 0.70f)
    p.cubicTo(w * 0.38f, h * 0.76f, w * 0.37f, h * 0.79f, w * 0.36f, h * 0.81f)
    p.cubicTo(w * 0.45f, h * 0.76f, w * 0.55f, h * 0.76f, w * 0.64f, h * 0.81f)
    p.cubicTo(w * 0.63f, h * 0.79f, w * 0.62f, h * 0.76f, w * 0.60f, h * 0.70f)
    p.cubicTo(w * 0.57f, h * 0.62f, w * 0.54f, h * 0.52f, w * 0.50f, h * 0.48f)
    p.close()
    return p
}

//pared de adentro de un ala, la franja que se ve junto a la abertura
private fun innerWallPath(w: Float, h: Float, left: Boolean): Path {
    val x = { v: Float -> if (left) w * v else w * (1f - v) }
    val p = Path()
    p.moveTo(x(0.50f), h * 0.48f)
    p.cubicTo(x(0.46f), h * 0.52f, x(0.43f), h * 0.62f, x(0.40f), h * 0.70f)
    p.cubicTo(x(0.38f), h * 0.76f, x(0.35f), h * 0.82f, x(0.31f), h * 0.86f)
    p.cubicTo(x(0.36f), h * 0.74f, x(0.40f), h * 0.64f, x(0.44f), h * 0.58f)
    p.cubicTo(x(0.46f), h * 0.54f, x(0.48f), h * 0.51f, x(0.50f), h * 0.48f)
    p.close()
    return p
}

//la galleta completa con su volumen, el hueco, el pliegue, el brillo y puntitos de horneado
private fun DrawScope.drawWholeCookie() {
    val w = size.width
    val h = size.height
    val body = cookiePath(w, h)
    drawPath(path = body, brush = Brush.radialGradient(listOf(CrustLight, CrustMid, CrustDark), center = Offset(w * 0.40f, h * 0.32f), radius = w * 0.7f))
    //hueco oscuro y las dos paredes de adentro
    drawPath(path = cavityPath(w, h), color = CrustShadow)
    drawPath(path = innerWallPath(w, h, true), color = CrustDark)
    drawPath(path = innerWallPath(w, h, false), color = CrustDark)
    //raya suave del pliegue, del lomo hasta la abertura
    val crease = Path()
    crease.moveTo(w * 0.50f, h * 0.20f)
    crease.cubicTo(w * 0.52f, h * 0.30f, w * 0.52f, h * 0.40f, w * 0.50f, h * 0.48f)
    drawPath(path = crease, color = CrustDark.copy(alpha = 0.45f), style = Stroke(width = w * 0.012f, cap = StrokeCap.Round))
    //brillo sobre el lomo
    val ridge = Path()
    ridge.moveTo(w * 0.12f, h * 0.48f)
    ridge.cubicTo(w * 0.16f, h * 0.32f, w * 0.28f, h * 0.23f, w * 0.42f, h * 0.21f)
    drawPath(path = ridge, color = BackgroundLightColor.copy(alpha = 0.5f), style = Stroke(width = w * 0.03f, cap = StrokeCap.Round))
    //puntitos de horneado, siempre en el mismo lugar y fuera del hueco
    for (i in 0 until 16) {
        val px = 0.12f + ((i * 37) % 76) / 100f
        val py = 0.26f + ((i * 53) % 40) / 100f
        if (px > 0.36f && px < 0.64f && py > 0.45f) continue
        drawCircle(color = CrustDark.copy(alpha = 0.2f), radius = w * 0.008f, center = Offset(w * px, h * py))
    }
    drawPath(path = body, color = CrustShadow.copy(alpha = 0.35f), style = Stroke(width = w * 0.012f))
}

//migajas que salen del centro y se van apagando
private fun DrawScope.drawCrumbs(s: Float) {
    val origin = Offset(size.width * 0.5f, size.height * 0.45f)
    for (i in 0 until 12) {
        val angle = (i * 30.0 + 8.0) * PI / 180.0
        val distance = size.width * 0.6f * s * (0.6f + (i % 4) * 0.12f)
        //las migajas caen un poco con la gravedad
        val fall = size.height * 0.25f * s * s
        val center = Offset(origin.x + (cos(angle) * distance).toFloat(), origin.y + (sin(angle) * distance).toFloat() + fall)
        drawCircle(color = if (i % 2 == 0) CrustMid else CrustDark, radius = size.width * (0.015f + (i % 3) * 0.007f), center = center, alpha = (1f - s).coerceIn(0f, 1f))
    }
}
