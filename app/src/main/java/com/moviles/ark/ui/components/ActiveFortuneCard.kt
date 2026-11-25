package com.moviles.ark.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor

//el papelito de la fortuna, el mismo papel ondulado que sale de la galleta con la frase escrita
//queda entre las dos mitades, que tapan sus puntas
@Composable
fun FortuneGlassPaper(quote: String, modifier: Modifier = Modifier, alpha: Float = 1f, quoteSize: Int = 20) {
    val paper = Modifier.drawBehind { drawFortunePaper(0f, 0f, size.width, size.height, FortunePaperHeight.toPx() * 0.035f) }
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }.padding(horizontal = FortunePaperSidePadding).fillMaxWidth().heightIn(min = FortunePaperHeight).then(paper).padding(horizontal = 22.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
        Text(text = quote, style = MaterialTheme.typography.headlineLarge.copy(fontSize = quoteSize.sp, lineHeight = (quoteSize + 7).sp), color = TextColor, textAlign = TextAlign.Center)
    }
}

//los botones de la frase, guardar con el corazon y compartir como imagen
@Composable
fun FortuneActions(isKept: Boolean, isSharing: Boolean, onToggleFavorite: () -> Unit, onShare: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val keepIcon = if (isKept) Icons.Default.Favorite else Icons.Default.FavoriteBorder
        FortunePill(text = if (isKept) "Kept" else "Keep", icon = keepIcon, iconTint = AccentColor, onClick = onToggleFavorite, modifier = Modifier.width(128.dp))
        FortunePill(text = "Share", icon = Icons.Default.Share, iconTint = SecondaryColor, enabled = !isSharing, loading = isSharing, onClick = onShare, modifier = Modifier.width(128.dp))
    }
}

//botones que aparecen debajo de la mesa cuando la frase ya se lee
//mainText es el boton naranja, secondText un boton de texto opcional debajo
@Composable
fun FortuneRevealActions(isKept: Boolean, isSharing: Boolean, mainText: String, onToggleFavorite: () -> Unit, onShare: () -> Unit, onMain: () -> Unit, modifier: Modifier = Modifier, secondText: String? = null, onSecond: () -> Unit = {}) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FortuneActions(isKept = isKept, isSharing = isSharing, onToggleFavorite = onToggleFavorite, onShare = onShare)
        Spacer(modifier = Modifier.height(12.dp))
        FortunePill(text = mainText, icon = null, solidColor = PrimaryColor, onClick = onMain, modifier = Modifier.width(268.dp))
        if (secondText != null) {
            TextButton(onClick = onSecond, modifier = Modifier.padding(top = 2.dp)) {
                Text(text = secondText, style = MaterialTheme.typography.labelLarge, color = lerpMuted())
            }
        }
    }
}

//cafe suave para el boton de texto
private fun lerpMuted() = FortuneMutedText
