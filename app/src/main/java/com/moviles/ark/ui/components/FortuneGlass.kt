package com.moviles.ark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.ScreenTopPadding
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor

//color del papelito, un crema entre el fondo y el fondo claro, sin llegar a blanco
val FortunePaperColor = lerp(BackgroundColor, BackgroundLightColor, 0.4f)

//fondo calido con luces suaves de la paleta, le da color al vidrio que va encima
@Composable
fun FortuneBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val gradient = Brush.verticalGradient(listOf(BackgroundLightColor, BackgroundColor, lerp(BackgroundColor, PrimaryColor, 0.22f)))
    Box(modifier = modifier.fillMaxSize().background(gradient).drawBehind { drawGlows() }, content = content)
}

//tres luces difusas, teal arriba, naranja al lado y coral abajo
private fun DrawScope.drawGlows() {
    glow(SecondaryColor.copy(alpha = 0.20f), Offset(size.width * 0.05f, size.height * 0.10f), size.width * 0.8f)
    glow(PrimaryColor.copy(alpha = 0.26f), Offset(size.width * 0.95f, size.height * 0.45f), size.width * 0.75f)
    glow(AccentColor.copy(alpha = 0.12f), Offset(size.width * 0.2f, size.height * 0.95f), size.width * 0.7f)
}

//un circulo de luz que se desvanece hacia afuera
private fun DrawScope.glow(color: Color, center: Offset, radius: Float) {
    drawCircle(brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius), radius = radius, center = center)
}

//vidrio liquido, relleno transparente de la paleta, brillo arriba y borde de luz
fun Modifier.liquidGlass(shape: Shape): Modifier {
    val fill = Brush.verticalGradient(listOf(FortunePaperColor.copy(alpha = 0.7f), BackgroundColor.copy(alpha = 0.35f)))
    val edge = Brush.verticalGradient(listOf(BackgroundLightColor.copy(alpha = 0.8f), PrimaryColor.copy(alpha = 0.25f)))
    return this.clip(shape).background(fill).drawBehind { drawShine() }.border(1.dp, edge, shape)
}

//brillo en la mitad de arriba del vidrio
private fun DrawScope.drawShine() {
    val shine = Brush.verticalGradient(listOf(BackgroundLightColor.copy(alpha = 0.3f), Color.Transparent), endY = size.height * 0.5f)
    drawRect(brush = shine, size = Size(size.width, size.height * 0.5f))
}

//encabezado como en las demas herramientas, boton atras redondo y titulo grande
@Composable
fun FortuneHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = ScreenTopPadding, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(SecondaryColor).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextColor, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.headlineLarge, color = TextColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

//boton de pildora, con color lleno para la accion principal o de vidrio para las demas
@Composable
fun FortunePill(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, solidColor: Color? = null, iconTint: Color = TextColor, enabled: Boolean = true) {
    val shape = RoundedCornerShape(26.dp)
    val surface = if (solidColor != null) Modifier.clip(shape).background(solidColor) else Modifier.liquidGlass(shape)
    Row(modifier = modifier.height(52.dp).then(surface).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = TextColor, maxLines = 1)
    }
}

//snackbars con la paleta, fondo del color del texto y letra clara
@Composable
fun FortuneSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState = hostState) { data ->
        Snackbar(snackbarData = data, containerColor = TextColor, contentColor = BackgroundLightColor, actionColor = PrimaryColor, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}
