package com.moviles.ark.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.moviles.ark.domain.models.WishModel
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.WishLanternUiState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    onFinish: () -> Unit
) {
    //cielo de noche: cafe oscuro con un brillo naranja abajo
    val skyGlow = Brush.verticalGradient(listOf(Color.Transparent, PrimaryColor.copy(alpha = 0.3f)))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TextColor)
            .background(skyGlow)
    ) {
        //capa de atras: los faroles de los deseos
        SkyLanterns(
            wishes = uiState.skyWishes(),
            litWishIds = uiState.litWishIds,
            onLanternTapped = onLanternTapped
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
            WishOfflineMessage(isOnline = uiState.isOnline, message = WISHES_OFFLINE_MESSAGE)

            if (!uiState.isLoading && uiState.wishes.isEmpty()) {
                Text(
                    text = "Your sky is empty. Write your first wish.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BackgroundColor,
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
    wishes: List<WishModel>,
    litWishIds: Set<String>,
    onLanternTapped: (wishId: String) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 120.dp)
    ) {
        //los faroles solo ocupan la parte de arriba, para no tapar el farol grande
        val skyWidth = maxWidth - 40.dp
        val skyHeight = maxHeight * 0.5f

        for (wish in wishes) {
            val isLit = litWishIds.contains(wish.id)
            val position = lanternPosition(wish.id)

            var description = "Unlit lantern. Tap to light it and read the wish"
            if (isLit) {
                description = "Lit lantern. Tap to read the wish"
            }

            LanternDrawing(
                isLit = isLit,
                modifier = Modifier
                    .offset(x = skyWidth * position.first, y = skyHeight * position.second)
                    .size(width = 32.dp, height = 40.dp)
                    .clickable { onLanternTapped(wish.id) }
                    .semantics { contentDescription = description }
            )
        }
    }
}

//lugar de un farol en el cielo, como fraccion del ancho (first) y del alto (second), de 0 a 1
//sale del id del deseo, asi cada farol queda siempre en el mismo lugar
private fun lanternPosition(wishId: String): Pair<Float, Float> {
    //numero entre 0 y 9999 que siempre es el mismo para el mismo id
    val number = Math.floorMod(wishId.hashCode(), 10000)
    val x = (number % 100) / 100f
    val y = (number / 100) / 100f
    return Pair(x, y)
}

//farol grande encendido: se desliza hacia arriba para mandarlo a volar
@Composable
private fun ReleasableLantern(wish: WishModel, onReleased: () -> Unit) {
    //cuanto ha subido el farol, en pixeles (negativo = hacia arriba)
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    //se va desvaneciendo mientras sube
    var lanternAlpha = 1f + offsetY.value / 1500f
    if (lanternAlpha < 0f) {
        lanternAlpha = 0f
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .alpha(lanternAlpha)
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
            modifier = Modifier.padding(horizontal = 40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        LanternDrawing(isLit = true, modifier = Modifier.size(width = 140.dp, height = 180.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Swipe up to release your lantern",
            style = MaterialTheme.typography.labelLarge,
            color = BackgroundColor
        )
    }
}

//farol grande apagado: espera un deseo nuevo
@Composable
private fun UnlitBigLantern(onWriteWish: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LanternDrawing(isLit = false, modifier = Modifier.size(width = 140.dp, height = 180.dp))
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
        OutlinedButton(onClick = onSeeAllWishes) {
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
            LanternDrawing(isLit = true, modifier = Modifier.size(width = 48.dp, height = 60.dp))
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
    val wishes = listOf(
        WishModel("1", "To travel with my family", 0L, 0L),
        WishModel("2", "To finish the semester calm", 0L, 0L),
        WishModel("3", "To sleep better", 0L, 0L)
    )
    AppTheme {
        WishSkyScreen(
            uiState = WishLanternUiState(wishes = wishes, litWishIds = setOf("1", "3"), isLoading = false),
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
