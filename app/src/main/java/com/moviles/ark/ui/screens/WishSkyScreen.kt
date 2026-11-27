package com.moviles.ark.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.moviles.ark.domain.models.WishModel
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.SkyLantern
import com.moviles.ark.ui.viewmodels.WishLanternUiState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

//cuanto tarda un farol del fondo en aparecer y en desvanecerse cuando sale del cielo
private const val APPEAR_FADE_MILLIS = 800
private const val LEAVING_FADE_MILLIS = 900
//color del brillo del farol recien lanzado: amarillo calido
private val HighlightColor = Color(0xFFFFE08A)

//cuantos pixeles hay que deslizar hacia arriba para que el farol salga volando
private const val RELEASE_DRAG_PIXELS = 250f
//hasta donde sube el farol cuando sale volando (mas alto que cualquier pantalla)
private const val FLY_AWAY_PIXELS = -3000f

//vista 1: el cielo
//al frente el farol grande; atras los faroles de todos los deseos, encendidos o apagados
@Composable
fun WishSkyScreen(
    uiState: WishLanternUiState,
    onBack: () -> Unit,
    onLanternTapped: (wishId: String) -> Unit,
    onCloseOpenedWish: () -> Unit,
    onLanternReleased: () -> Unit,
    onWriteWish: () -> Unit,
    onSeeAllWishes: () -> Unit,
    onFinish: () -> Unit,
    //la pantalla mide el cielo y se lo dice al viewmodel, para acomodar los faroles sin que se toquen
    onSkySizeMeasured: (widthDp: Float, heightDp: Float) -> Unit = { _, _ -> }
) {
    Box(modifier = Modifier.fillMaxSize()) {
        //capa del fondo: el paisaje de noche
        NightLandscape(modifier = Modifier.fillMaxSize())

        //capa de atras: los faroles de los deseos
        SkyLanterns(
            lanterns = uiState.skyLanternsToShow(),
            leavingLanterns = uiState.leavingLanterns,
            litWishIds = uiState.litWishIds,
            highlightedWishId = uiState.highlightedWishId,
            onLanternTapped = onLanternTapped,
            onSkySizeMeasured = onSkySizeMeasured
        )

        //capa de adelante: titulo, farol grande y botones
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WishHeader(title = "Wish Lantern", onBack = onBack, textColor = BackgroundLightColor)
            WishOfflineMessage(message = wishesNotice(uiState))

            if (uiState.isSkyEmpty()) {
                Text(
                    text = "Your sky is empty. Write your first wish.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BackgroundLightColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp)
                )
            }

            //empuja el farol grande hacia abajo
            Spacer(modifier = Modifier.weight(1f))

            val wishToRelease = uiState.wishToRelease
            if (wishToRelease != null) {
                ReleasableLantern(wish = wishToRelease, onReleased = onLanternReleased)
            } else {
                UnlitBigLantern(onWriteWish = onWriteWish)
            }

            Spacer(modifier = Modifier.height(24.dp))
            SkyBottomButtons(onSeeAllWishes = onSeeAllWishes, onFinish = onFinish)
        }
    }

    //deseo abierto al tocar un farol: se muestra abajo, solo para leerlo
    val openedWish = uiState.openedWish
    if (openedWish != null) {
        OpenedWishSheet(wish = openedWish, onClose = onCloseOpenedWish)
    }
}

//faroles pequenos del fondo; cada uno se puede tocar para leer su deseo
@Composable
private fun SkyLanterns(
    lanterns: List<SkyLantern>,
    leavingLanterns: List<SkyLantern>,
    litWishIds: Set<String>,
    highlightedWishId: String?,
    onLanternTapped: (wishId: String) -> Unit,
    onSkySizeMeasured: (widthDp: Float, heightDp: Float) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 120.dp)
    ) {
        //los faroles solo ocupan la parte de arriba, para no tapar el farol grande
        val skyWidth = maxWidth - 48.dp
        val skyHeight = maxHeight * 0.45f

        //se le dice al viewmodel cuanto mide el cielo en este telefono (solo cuando cambia)
        LaunchedEffect(skyWidth, skyHeight) {
            onSkySizeMeasured(skyWidth.value, skyHeight.value)
        }

        //se dibujan los que estan y los que se estan yendo
        //key: cada farol se reconoce por su id, asi al pasar de "esta" a "se va" no se dibuja de cero
        //y puede desvanecerse en vez de desaparecer de golpe
        val allLanterns = mutableListOf<SkyLantern>()
        val visibleIds = mutableSetOf<String>()
        for (lantern in lanterns) {
            allLanterns.add(lantern)
            visibleIds.add(lantern.wishId)
        }
        val leavingIds = mutableSetOf<String>()
        for (lantern in leavingLanterns) {
            //si un farol se fue y justo volvio a entrar, se dibuja solo una vez (el que esta)
            if (!visibleIds.contains(lantern.wishId)) {
                allLanterns.add(lantern)
                leavingIds.add(lantern.wishId)
            }
        }

        for (lantern in allLanterns) {
            key(lantern.wishId) {
                FloatingLantern(
                    isLit = litWishIds.contains(lantern.wishId),
                    isLeaving = leavingIds.contains(lantern.wishId),
                    isHighlighted = lantern.wishId == highlightedWishId,
                    //la posicion de cada farol ya viene calculada desde el viewmodel (en Dispatchers.Default)
                    x = skyWidth * lantern.x,
                    y = skyHeight * lantern.y,
                    lanternWidth = lantern.widthDp().dp,
                    floatDurationMillis = lantern.floatDurationMillis,
                    onTap = { onLanternTapped(lantern.wishId) }
                )
            }
        }
    }
}

//un farol del fondo que sube y baja despacio, como si flotara en el aire
//- al aparecer, se hace visible poco a poco
//- si se esta yendo del cielo, se desvanece
//- si es el que se acaba de lanzar, tiene un brillo que late para que el usuario vea donde quedo
@Composable
private fun FloatingLantern(
    isLit: Boolean,
    isLeaving: Boolean,
    isHighlighted: Boolean,
    x: Dp,
    y: Dp,
    lanternWidth: Dp,
    //cada farol flota a su propio ritmo para que no se muevan todos iguales
    floatDurationMillis: Int,
    onTap: () -> Unit
) {
    val floatTransition = rememberInfiniteTransition(label = "floatingLantern")
    val floatDistance by floatTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = floatDurationMillis), repeatMode = RepeatMode.Reverse),
        label = "floatDistance"
    )

    //que tan visible esta el farol: empieza invisible (0) y aparece (1); si se va, vuelve a 0
    val visibility = remember { Animatable(0f) }
    LaunchedEffect(isLeaving) {
        if (isLeaving) {
            visibility.animateTo(0f, animationSpec = tween(durationMillis = LEAVING_FADE_MILLIS))
        } else {
            visibility.animateTo(1f, animationSpec = tween(durationMillis = APPEAR_FADE_MILLIS))
        }
    }

    var description = "Unlit lantern. Tap to light it and read the wish"
    if (isLit) {
        description = "Lit lantern. Tap to read the wish"
    }

    Box(
        modifier = Modifier
            .offset(x = x, y = y + floatDistance.dp)
            .size(width = lanternWidth, height = lanternWidth * 1.25f)
            .alpha(visibility.value),
        contentAlignment = Alignment.Center
    ) {
        if (isHighlighted) {
            HighlightGlow(lanternWidth = lanternWidth)
        }
        LanternDrawing(
            isLit = isLit,
            modifier = Modifier
                .fillMaxSize()
                //un farol que se esta yendo ya no se puede tocar
                .clickable(enabled = !isLeaving) { onTap() }
                .semantics { contentDescription = description }
        )
    }
}

//brillo que crece y se achica detras del farol recien lanzado
@Composable
private fun HighlightGlow(lanternWidth: Dp) {
    val pulseTransition = rememberInfiniteTransition(label = "highlightPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 700), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    //el brillo es mas grande que el farol: se sale de sus bordes
    Canvas(modifier = Modifier.requiredSize(lanternWidth * 3)) {
        val glow = Brush.radialGradient(
            colors = listOf(HighlightColor.copy(alpha = 0.7f * pulse), Color.Transparent)
        )
        drawCircle(brush = glow, radius = size.minDimension / 2 * pulse)
    }
}

//farol grande encendido: se desliza hacia arriba para mandarlo a volar
@Composable
private fun ReleasableLantern(wish: WishModel, onReleased: () -> Unit) {
    //cuanto ha subido el farol, en pixeles (negativo = hacia arriba)
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    //mientras sube se va desvaneciendo y se hace mas pequeno, como si se alejara
    var lanternAlpha = 1f + offsetY.value / 1500f
    if (lanternAlpha < 0f) {
        lanternAlpha = 0f
    }
    var lanternScale = 1f + offsetY.value / 2000f
    if (lanternScale < 0.3f) {
        lanternScale = 0.3f
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .alpha(lanternAlpha)
            .scale(lanternScale)
            .pointerInput(wish.id) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        //solo se deja mover hacia arriba
                        var newOffset = offsetY.value + dragAmount
                        if (newOffset > 0f) {
                            newOffset = 0f
                        }
                        scope.launch { offsetY.snapTo(newOffset) }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (offsetY.value < -RELEASE_DRAG_PIXELS) {
                                //lo subio lo suficiente: sale volando
                                offsetY.animateTo(FLY_AWAY_PIXELS, animationSpec = tween(durationMillis = 1200))
                                onReleased()
                            } else {
                                //no lo subio lo suficiente: vuelve a su lugar
                                offsetY.animateTo(0f)
                            }
                        }
                    }
                )
            }
            .semantics { contentDescription = "Lit lantern. Swipe up to release it" }
    ) {
        Text(
            text = wish.text,
            style = MaterialTheme.typography.bodyLarge,
            color = BackgroundLightColor,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TextColor.copy(alpha = 0.3f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        LanternDrawing(isLit = true, modifier = Modifier.size(width = 140.dp, height = 175.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Swipe up to release your lantern",
            style = MaterialTheme.typography.labelLarge,
            color = BackgroundLightColor
        )
    }
}

//farol grande apagado: espera un deseo nuevo
@Composable
private fun UnlitBigLantern(onWriteWish: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LanternDrawing(isLit = false, modifier = Modifier.size(width = 140.dp, height = 175.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onWriteWish,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = TextColor)
        ) {
            Text(text = "Write a wish", style = MaterialTheme.typography.labelLarge)
        }
    }
}

//abajo: ver todos los deseos y el chulito para terminar
@Composable
private fun SkyBottomButtons(onSeeAllWishes: () -> Unit, onFinish: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onSeeAllWishes,
            border = BorderStroke(1.dp, BackgroundLightColor.copy(alpha = 0.7f)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = TextColor.copy(alpha = 0.25f))
        ) {
            Text(text = "See all my wishes", style = MaterialTheme.typography.labelLarge, color = BackgroundLightColor)
        }
        WishRoundButton(icon = Icons.Default.Check, label = "Finish", onClick = onFinish)
    }
}

//hoja de abajo con el deseo de un farol; solo se puede leer
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenedWishSheet(wish: WishModel, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = BackgroundLightColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LanternDrawing(isLit = true, modifier = Modifier.size(width = 56.dp, height = 70.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = wish.text,
                style = MaterialTheme.typography.titleMedium,
                color = TextColor,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatWishDate(wish.createdAt),
                style = MaterialTheme.typography.bodyMedium,
                color = TextColor.copy(alpha = 0.6f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WishSkyScreenPreview() {
    val lanterns = listOf(
        SkyLantern(wishId = "1", x = 0.1f, y = 0.2f, floatDurationMillis = 2000),
        SkyLantern(wishId = "2", x = 0.5f, y = 0.6f, floatDurationMillis = 2600),
        SkyLantern(wishId = "3", x = 0.8f, y = 0.1f, floatDurationMillis = 3200)
    )
    AppTheme {
        WishSkyScreen(
            uiState = WishLanternUiState(skyLanterns = lanterns, litWishIds = setOf("1", "3"), isSkyLoading = false),
            onBack = {},
            onLanternTapped = {},
            onCloseOpenedWish = {},
            onLanternReleased = {},
            onWriteWish = {},
            onSeeAllWishes = {},
            onFinish = {}
        )
    }
}
