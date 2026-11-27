package com.moviles.ark.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.foundation.layout.navigationBarsPadding
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.ScreenTopPadding
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.sp

import androidx.compose.ui.draw.shadow

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
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor

//color del papelito, un crema entre el fondo y el fondo claro, sin llegar a blanco
val FortunePaperColor = BackgroundColor

//color suave para textos de apoyo, el cafe del texto con menos fuerza
val FortuneMutedText = TextColor.copy(alpha = 0.62f)

//fondo calido de la paleta con una sola luz naranja suave detras de la mesa
@Composable
fun FortuneBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    //del crema de la paleta al durazno, sin llegar a blanco arriba
    val gradient = Brush.verticalGradient(listOf(lerp(BackgroundColor, PrimaryColor, 0.14f), lerp(BackgroundColor, PrimaryColor, 0.24f), lerp(BackgroundColor, PrimaryColor, 0.34f)))
    Box(modifier = modifier.fillMaxSize().background(gradient).drawBehind { drawWarmLight() }, content = content)
}

//luz naranja difusa a la altura de la mesa
private fun DrawScope.drawWarmLight() {
    val center = Offset(size.width * 0.5f, size.height * 0.42f)
    val radius = size.width * 0.9f
    drawCircle(brush = Brush.radialGradient(listOf(PrimaryColor.copy(alpha = 0.16f), Color.Transparent), center = center, radius = radius), radius = radius, center = center)
}

//vidrio liquido, relleno claro transparente, brillo arriba y borde de luz
fun Modifier.liquidGlass(shape: Shape): Modifier {
    //vidrio crema con un toque naranja, sombra calida y borde naranja para que resalte sin verse blanco
    //vidrio difuminado del color de fondo de la paleta, resalta porque el fondo de la pantalla es mas naranja
    val fill = Brush.verticalGradient(listOf(BackgroundColor.copy(alpha = 0.82f), BackgroundColor.copy(alpha = 0.66f)))
    val edge = Brush.verticalGradient(listOf(BackgroundColor, PrimaryColor.copy(alpha = 0.45f)))
    return this.shadow(elevation = 6.dp, shape = shape, ambientColor = PrimaryColor.copy(alpha = 0.35f), spotColor = PrimaryColor.copy(alpha = 0.35f)).clip(shape).background(fill).drawBehind { drawShine() }.border(1.2.dp, edge, shape)
}

//brillo en la mitad de arriba del vidrio
private fun DrawScope.drawShine() {
    val shine = Brush.verticalGradient(listOf(BackgroundColor.copy(alpha = 0.5f), Color.Transparent), endY = size.height * 0.5f)
    drawRect(brush = shine, size = Size(size.width, size.height * 0.5f))
}

//boton redondo de vidrio solo con icono
@Composable
fun FortuneIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = TextColor) {
    Box(modifier = modifier.size(44.dp).liquidGlass(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

//encabezado igual al de las demas herramientas: boton redondo verde azulado para volver y el titulo grande al lado
@Composable
fun FortuneHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = ScreenTopPadding, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(SecondaryColor).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextColor, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        FortuneTitle(text = title, modifier = Modifier.weight(1f))
        trailing()
    }
}

//titulo grande que se achica solo si no cabe en el ancho del telefono
@Composable
private fun FortuneTitle(text: String, modifier: Modifier = Modifier) {
    var fontSize by remember(text) { mutableStateOf(34f) }
    var ready by remember(text) { mutableStateOf(false) }
    Text(text = text, style = MaterialTheme.typography.headlineLarge.copy(fontSize = fontSize.sp, lineHeight = (fontSize + 6f).sp), color = TextColor, maxLines = 1, softWrap = false, textAlign = TextAlign.Start, modifier = modifier.drawWithContent { if (ready) drawContent() }, onTextLayout = { layout ->
        if (layout.hasVisualOverflow && fontSize > 20f) fontSize -= 1f else ready = true
    })
}

//boton de pildora, con color lleno para la accion principal o de vidrio para las demas
@Composable
fun FortunePill(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, solidColor: Color? = null, iconTint: Color = TextColor, enabled: Boolean = true, loading: Boolean = false, textColor: Color = TextColor) {
    val shape = RoundedCornerShape(26.dp)
    val surface = if (solidColor != null) Modifier.clip(shape).background(solidColor) else Modifier.liquidGlass(shape)
    Row(modifier = modifier.height(50.dp).then(surface).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        //mientras se prepara algo sale una ruedita en vez del icono, el texto no cambia
        if (loading) {
            CircularProgressIndicator(color = iconTint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = textColor, maxLines = 1)
    }
}

//aviso pequeno de vidrio cuando no hay internet
@Composable
fun FortuneOfflineNotice(text: String, modifier: Modifier = Modifier) {
    //pildora suave del color de la paleta, sin caja blanca
    Row(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(PrimaryColor.copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Default.CloudOff, contentDescription = null, tint = lerp(PrimaryColor, TextColor, 0.35f), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = TextColor)
    }
}

//los mensajes largos se quedan mas tiempo en pantalla para que alcancen a leerse
fun fortuneSnackbarDuration(message: String): SnackbarDuration = if (message.length > 40) SnackbarDuration.Long else SnackbarDuration.Short

//snackbars con la paleta, fondo del color del texto y letra clara
@Composable
fun FortuneSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState = hostState) { data ->
        Snackbar(snackbarData = data, containerColor = TextColor, contentColor = BackgroundColor, actionColor = PrimaryColor, shape = RoundedCornerShape(16.dp), modifier = Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 28.dp))
    }
}
