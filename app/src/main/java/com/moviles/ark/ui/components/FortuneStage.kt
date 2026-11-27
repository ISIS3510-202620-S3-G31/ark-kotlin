package com.moviles.ark.ui.components

import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalContext
import android.media.MediaPlayer
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.moviles.ark.R
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.viewmodels.FortunePhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

//sonidos hechos con numeros, no necesitan archivos de audio
object CookieCrunchSoundPlayer {
    //crujido de galleta: muchos chasquidos cortitos y agudos, primero un grupo fuerte, luego otro y al final migajas
    //si en res/raw hay un archivo cookie_crack se usa ese sonido grabado, si no se arma con numeros
    fun playCrunch(context: Context) {
        val recorded = context.resources.getIdentifier("cookie_crack", "raw", context.packageName)
        if (recorded != 0) {
            try {
                val player = MediaPlayer.create(context, recorded)
                if (player != null) {
                    player.setOnCompletionListener { it.release() }
                    player.start()
                    return
                }
            } catch (_: Exception) {
                //si falla se usa el sonido hecho con numeros
            }
        }
        val sampleRate = 44100
        val samples = sampleRate * 340 / 1000
        val random = Random()
        val raw = DoubleArray(samples)
        //un chasquido es ruido de unos pocos milisegundos que se apaga muy rapido
        fun crackle(start: Double, amplitude: Double) {
            val first = (start * sampleRate).toInt()
            val length = ((1.0 + random.nextDouble() * 3.0) * sampleRate / 1000).toInt()
            val decay = (0.3 + random.nextDouble() * 0.6) * sampleRate / 1000
            for (k in 0 until length) {
                if (first + k < samples) raw[first + k] += (random.nextDouble() * 2.0 - 1.0) * exp(-k / decay) * amplitude
            }
        }
        //grupo de chasquidos que se van separando y bajando de fuerza
        fun cluster(start: Double, count: Int, spread: Double, from: Double, to: Double) {
            for (i in 0 until count) {
                val progress = i.toDouble() / count
                val time = start + spread * Math.pow(progress, 1.6) + random.nextDouble() * 0.002
                crackle(time, (from + (to - from) * progress) * (0.7 + 0.6 * random.nextDouble()))
            }
        }
        cluster(0.0, 14, 0.035, 1.0, 0.45)
        cluster(0.075, 8, 0.03, 0.6, 0.25)
        for (i in 0 until 30) {
            val time = 0.11 + random.nextDouble() * 0.2
            crackle(time, (0.06 + 0.2 * random.nextDouble()) * exp(-(time - 0.11) * 8.0))
        }
        //se le quitan los graves para que suene crocante y no a golpe
        var peak = 0.0
        val filtered = DoubleArray(samples)
        for (i in 0 until samples) {
            filtered[i] = raw[i] - (if (i > 0) 0.85 * raw[i - 1] else 0.0)
            peak = maxOf(peak, kotlin.math.abs(filtered[i]))
        }
        val buffer = ShortArray(samples) { i -> (filtered[i] / maxOf(peak, 1e-6) * 0.9 * Short.MAX_VALUE).toInt().toShort() }
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

//alto que ocupa el papelito cuando ya quedo plano en el centro de la mesa
val FortunePaperHeight = 112.dp

//espacio a cada lado del papelito, las mitades de la galleta quedan encima de sus puntas
val FortunePaperSidePadding = 64.dp

//color de la luz de cada galleta, uno de la paleta para cada una
private val CookieLights = listOf(PrimaryColor, SecondaryColor, AccentColor)

//mesa con las 3 galletas del mismo tamano, una arriba y dos abajo, flotando sobre luces de colores
//la elegida va al centro, tiembla y se parte en dos mitades que se abren hacia los lados
//el papelito sale ondulado del medio, se estira y queda en el centro con una mitad a cada lado
//previewing muestra una frase reciente en el centro sin abrir galletas
@Composable
fun FortuneStage(phase: FortunePhase, chosenIndex: Int, hasChances: Boolean, previewing: Boolean, onCrackCookie: (Int) -> Unit, stageHeight: Dp, modifier: Modifier = Modifier, cardContent: @Composable (Float) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    //avance de cada parte de la animacion, de 0 a 1
    val travel = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val split = remember { Animatable(0f) }
    val ribbon = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }

    //la llave solo cambia al empezar o terminar de elegir, asi pasar de opening a revealed no corta la animacion
    LaunchedEffect(phase == FortunePhase.CHOOSING) {
        if (phase == FortunePhase.CHOOSING) {
            travel.snapTo(0f)
            shake.snapTo(0f)
            split.snapTo(0f)
            ribbon.snapTo(0f)
            fade.snapTo(0f)
            return@LaunchedEffect
        }
        //si la pantalla vuelve con la frase ya abierta todo queda en su sitio
        if (phase == FortunePhase.REVEALED && travel.value == 0f) {
            travel.snapTo(1f)
            shake.snapTo(1f)
            split.snapTo(1f)
            fade.snapTo(1f)
            ribbon.snapTo(1f)
            return@LaunchedEffect
        }
        //1. va al centro y crece
        travel.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        //2. tiembla tres veces con vibracion
        launch {
            for (i in 0 until 3) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(160)
            }
        }
        shake.animateTo(1f, tween(500, easing = LinearEasing))
        //3. se parte con su sonido
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        CookieCrunchSoundPlayer.playCrunch(context)
        split.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        //4. las mitades se van a los lados, dejando el hueco por donde sale el papel
        fade.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        //5. el papel sale del medio y se estira hasta las mitades
        CookieCrunchSoundPlayer.playRustle()
        ribbon.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }

    //frase reciente, el papel sale del centro sin galleta
    val preview = remember { Animatable(0f) }
    LaunchedEffect(previewing) {
        if (previewing) {
            CookieCrunchSoundPlayer.playRustle()
            preview.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        } else {
            preview.snapTo(0f)
        }
    }
    val cookiesAlpha by animateFloatAsState(if (previewing) 0f else 1f, tween(300), label = "cookiesAlpha")

    //las galletas solo flotan mientras se ven: al escoger, sin una reciente abierta y con la pantalla activa
    //al leer la frase, al ver una reciente o con el menu de compartir encima la animacion se detiene
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val cookiesOnScreen = phase == FortunePhase.CHOOSING && !previewing && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    //una vuelta del flotar cada 1.5 segundos, de 0 a 2 pi
    val floating = remember { Animatable(0f) }
    LaunchedEffect(cookiesOnScreen) {
        //si las galletas no se ven no se anima nada, y asi no se piden cuadros nuevos
        if (!cookiesOnScreen) return@LaunchedEffect
        while (true) {
            floating.animateTo((2 * PI).toFloat(), tween(1500, easing = LinearEasing))
            floating.snapTo(0f)
        }
    }
    val wave = floating.value

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(stageHeight)) {
        val localDensity = LocalDensity.current
        //las galletas son un poco mas grandes que sus luces
        val cookieSize = min(maxWidth * 0.34f, maxHeight * 0.34f)
        val lightSize = min(maxWidth * 0.3f, maxHeight * 0.3f)
        val stageWidthPx = with(localDensity) { maxWidth.toPx() }
        val stageHeightPx = with(localDensity) { maxHeight.toPx() }
        val paperWidthPx = stageWidthPx - with(localDensity) { (FortunePaperSidePadding * 2).toPx() }
        val paperHeightPx = with(localDensity) { FortunePaperHeight.toPx() }

        //capa de luz de cada galleta, el mismo lienzo para el disco de atras y el cilindro de adelante
        val lightModifier = { index: Int ->
            val slotX = if (index == 0) 0f else if (index == 1) -0.29f * stageWidthPx else 0.29f * stageWidthPx
            val slotY = if (index == 0) -0.3f * stageHeightPx else 0.06f * stageHeightPx
            Modifier.width(lightSize * 1.2f).height(lightSize * 2.5f).graphicsLayer {
                translationX = slotX
                translationY = slotY + lightSize.toPx() * 0.25f
                //la luz se apaga cuando se elige una galleta o se lee una reciente
                alpha = (1f - travel.value) * cookiesAlpha * (if (hasChances) 1f else 0.4f)
            }
        }

        //primero los discos del piso, asi quedan detras de todas las galletas
        for (index in 0..2) {
            val bob = sin(wave)
            Canvas(modifier = Modifier.align(Alignment.Center).then(lightModifier(index))) {
                //la luz respira con el flotar de su galleta
                drawCookieFloor(CookieLights[index], 0.85f + 0.15f * bob)
            }
        }

        for (index in 0..2) {
            val isChosen = index == chosenIndex
            //una arriba en el centro y dos abajo a los lados
            val slotX = if (index == 0) 0f else if (index == 1) -0.29f * stageWidthPx else 0.29f * stageWidthPx
            val slotY = if (index == 0) -0.3f * stageHeightPx else 0.06f * stageHeightPx
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val pressScale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
            val canOpen = phase == FortunePhase.CHOOSING && hasChances && !previewing
            val offset = index * 2.1f
            //todas suben y bajan al mismo tiempo
            val bob = sin(wave) * 6f

            val cookieLayer = Modifier.graphicsLayer {
                cameraDistance = 14f * density
                if (isChosen && phase != FortunePhase.CHOOSING) {
                    val t = travel.value
                    //temblor que crece y se apaga antes de partirse
                    val tremble = sin(shake.value * PI * 9).toFloat() * 7f * sin(shake.value * PI).toFloat()
                    translationX = slotX * (1f - t)
                    translationY = slotY * (1f - t)
                    scaleX = 1f + 0.4f * t
                    scaleY = 1f + 0.4f * t
                    rotationZ = tremble
                } else if (phase == FortunePhase.CHOOSING) {
                    translationX = slotX
                    translationY = slotY + bob * density
                    scaleX = pressScale
                    scaleY = pressScale
                    alpha = (if (hasChances) 1f else 0.45f) * cookiesAlpha
                } else {
                    //las que no se eligieron se encogen y se van
                    val t = travel.value
                    translationX = slotX * (1f + 0.15f * t)
                    translationY = slotY + 30f * density * t
                    scaleX = 1f - 0.3f * t
                    scaleY = 1f - 0.3f * t
                    alpha = 1f - t
                }
            }
            val open = isChosen && phase != FortunePhase.CHOOSING
            Box(modifier = Modifier.align(Alignment.Center).size(cookieSize).then(cookieLayer).clickable(interactionSource = interaction, indication = null, enabled = canOpen, role = Role.Button, onClickLabel = "Open this cookie", onClick = { onCrackCookie(index) })) {
                if (!(open && split.value > 0f)) {
                    Image(painter = painterResource(R.drawable.fortune_cookie_whole), contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
        }

        //el cilindro de luz va encima de las galletas y las tine de su color, igual que en la animacion
        for (index in 0..2) {
            val bob = sin(wave)
            Canvas(modifier = Modifier.align(Alignment.Center).then(lightModifier(index))) {
                drawCookieBeam(CookieLights[index], 0.85f + 0.15f * bob)
            }
        }

        //el papel queda en el centro y las mitades a los lados, encima de sus puntas
        val paperProgress = if (previewing) preview.value else ribbon.value
        //la frase aparece solo cuando el papel ya termino de salir
        val cardShown = paperProgress >= 1f && (phase == FortunePhase.REVEALED || previewing)
        val cardAlpha by animateFloatAsState(if (cardShown) 1f else 0f, tween(350), label = "cardAlpha")
        if (paperProgress > 0f && cardAlpha < 1f) {
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - cardAlpha }) {
                drawPaperRibbon(paperProgress, paperWidthPx, paperHeightPx, Offset(size.width / 2f, size.height / 2f))
            }
        }
        if (cardAlpha > 0f) {
            Box(modifier = Modifier.align(Alignment.Center)) { cardContent(cardAlpha) }
        }

        //las mitades: al abrir se parten y se corren a los lados; al volver a leer una reciente aparecen ya abiertas
        val opening = chosenIndex in 0..2 && phase != FortunePhase.CHOOSING && split.value > 0f && !previewing
        if (opening || previewing) {
            val cookiePx = with(localDensity) { cookieSize.toPx() }
            val paperEdgePx = with(localDensity) { ((maxWidth - FortunePaperSidePadding * 2) / 2f + 18.dp).toPx() }
            //cuanto se corre cada mitad para que su centro quede justo afuera de la punta del papel
            val spreadPx = (paperEdgePx / 1.3f - cookiePx * 0.2f - cookiePx * 0.32f).coerceAtLeast(0f)
            val splitValue = if (previewing) 1f else split.value
            val sideValue = if (previewing) 1f else fade.value
            val halvesAlpha = if (previewing) (preview.value * 3f).coerceIn(0f, 1f) else 1f
            Box(modifier = Modifier.align(Alignment.Center).size(cookieSize).graphicsLayer {
                scaleX = 1.4f - 0.1f * sideValue
                scaleY = 1.4f - 0.1f * sideValue
                alpha = halvesAlpha
            }) {
                Image(painter = painterResource(R.drawable.fortune_cookie_left), contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer { halfLayer(splitValue, -1f, spreadPx * sideValue) })
                Image(painter = painterResource(R.drawable.fortune_cookie_right), contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer { halfLayer(splitValue, 1f, spreadPx * sideValue) })
                if (!previewing) Canvas(modifier = Modifier.fillMaxSize()) { drawCookieShards(split.value) }
            }
        }
    }
}

//abre una mitad, side es -1 para la izquierda y 1 para la derecha
//gira desde su punta de abajo, se separa hacia afuera y se inclina un poco en 3d
//spread es lo que se corre de mas al final para dejar la frase en medio
private fun androidx.compose.ui.graphics.GraphicsLayerScope.halfLayer(s: Float, side: Float, spread: Float) {
    cameraDistance = 12f * density
    transformOrigin = TransformOrigin(if (side < 0f) 0.3f else 0.75f, 0.92f)
    translationX = side * (size.width * 0.32f * s + spread)
    translationY = -size.height * 0.03f * s
    rotationZ = side * 20f * s
    rotationY = -side * 16f * s
}
