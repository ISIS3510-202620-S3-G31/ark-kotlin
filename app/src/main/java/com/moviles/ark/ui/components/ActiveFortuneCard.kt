package com.moviles.ark.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviles.ark.domain.models.FortuneSource
import com.moviles.ark.domain.models.TableFortune
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor

//texto corto de donde salio la frase, sin la palabra saved para no confundirlo con guardar
fun fortuneSourceText(source: FortuneSource, isOffline: Boolean): String {
    if (source == FortuneSource.INTERNET) return "Just downloaded"
    if (source == FortuneSource.PHONE) return if (isOffline) "Offline, downloaded earlier" else "Downloaded earlier"
    return if (isOffline) "Offline, one of Ark's own" else "One of Ark's own"
}

//icono que acompana el texto de donde salio la frase
private fun fortuneSourceIcon(source: FortuneSource, isOffline: Boolean): ImageVector {
    if (isOffline) return Icons.Default.CloudOff
    if (source == FortuneSource.INTERNET) return Icons.Default.Cloud
    if (source == FortuneSource.PHONE) return Icons.Default.PhoneAndroid
    return Icons.Default.AutoAwesome
}

//papelito que sale de la galleta y los botones para guardar, compartir y terminar
@Composable
fun ActiveFortuneCard(opened: TableFortune, isSharing: Boolean, onToggleFavorite: () -> Unit, onShare: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val fortune = opened.fortune
    //el papel sube desde el pliegue, se desenrolla y luego aparece el texto
    val emerge = remember(fortune.id) { Animatable(0f) }
    LaunchedEffect(fortune.id) {
        CookieCrunchSoundPlayer.playRustle()
        emerge.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    val paperLayer = Modifier.graphicsLayer {
        val rise = (emerge.value / 0.35f).coerceIn(0f, 1f)
        val unroll = ((emerge.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(0.5f, 0f)
        translationY = -(1f - rise) * 70.dp.toPx()
        scaleX = 0.16f + 0.84f * unroll
        scaleY = 0.3f + 0.7f * unroll
        alpha = rise
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FortunePaper(quote = fortune.quote, modifier = paperLayer, textAlpha = { ((emerge.value - 0.7f) / 0.3f).coerceIn(0f, 1f) }) {
            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = fortuneSourceIcon(opened.source, opened.isOffline), contentDescription = null, tint = if (opened.isOffline) AccentColor else SecondaryColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = fortuneSourceText(opened.source, opened.isOffline), style = MaterialTheme.typography.bodySmall, color = TextColor.copy(alpha = 0.75f))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        //los botones aparecen cuando ya se lee la frase
        Column(modifier = Modifier.graphicsLayer { alpha = ((emerge.value - 0.75f) / 0.25f).coerceIn(0f, 1f) }, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FortunePill(text = if (fortune.isFavorite) "Kept" else "Keep", icon = if (fortune.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, iconTint = AccentColor, onClick = onToggleFavorite)
                FortunePill(text = if (isSharing) "Preparing..." else "Share", icon = Icons.Default.Share, iconTint = SecondaryColor, enabled = !isSharing, onClick = onShare)
            }
            Spacer(modifier = Modifier.height(14.dp))
            FortunePill(text = "I'm done", icon = null, solidColor = PrimaryColor, onClick = onDone, modifier = Modifier.width(220.dp))
        }
    }
}

//el papel de la fortuna, color claro de la paleta y dos lineas finas como los papelitos de verdad
@Composable
fun FortunePaper(quote: String, modifier: Modifier = Modifier, textAlpha: () -> Float = { 1f }, footer: @Composable () -> Unit = {}) {
    val shape = RoundedCornerShape(4.dp)
    val lines = Modifier.drawBehind {
        val inset = 14.dp.toPx()
        drawLine(PrimaryColor.copy(alpha = 0.35f), Offset(inset, inset), Offset(size.width - inset, inset), 1.dp.toPx())
        drawLine(PrimaryColor.copy(alpha = 0.35f), Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset), 1.dp.toPx())
    }
    Box(modifier = modifier.padding(horizontal = 24.dp).fillMaxWidth().shadow(10.dp, shape, ambientColor = TextColor, spotColor = TextColor).background(FortunePaperColor, shape).then(lines).padding(horizontal = 28.dp, vertical = 34.dp)) {
        Column(modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = textAlpha() }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = quote, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 21.sp, lineHeight = 31.sp), color = TextColor, textAlign = TextAlign.Center)
            footer()
        }
    }
}
