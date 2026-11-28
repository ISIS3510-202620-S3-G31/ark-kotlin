package com.moviles.ark.ui.components

import com.moviles.ark.ui.theme.BackgroundLightColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Rect

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

//colores de los pedacitos de galleta, los mismos tonos de la imagen
val CookieCrumbLight = lerp(BackgroundColor, PrimaryColor, 0.72f)
val CookieCrumbDark = lerp(PrimaryColor, TextColor, 0.3f)

//las luces imitan la animacion de lottie en pequeno: un disco de color en el piso y un cilindro de luz que sube
//el lienzo mide 1.2 veces la galleta de ancho y 2.5 de alto, la galleta queda entre 0.2 y 0.6 del alto
private const val LightLeft = 0.2f
private const val LightRight = 0.8f

//disco de color en el piso, va detras de la galleta
fun DrawScope.drawCookieFloor(color: Color, strength: Float) {
    val w = size.width
    val h = size.height
    //halo suave para que la galleta tambien se vea iluminada desde atras
    val haloCenter = Offset(w * 0.5f, h * 0.42f)
    drawCircle(brush = Brush.radialGradient(listOf(color.copy(alpha = 0.28f * strength), Color.Transparent), center = haloCenter, radius = w * 0.55f), radius = w * 0.55f, center = haloCenter)
    drawOval(color = color.copy(alpha = 0.95f * strength), topLeft = Offset(w * LightLeft, h * 0.685f), size = Size(w * (LightRight - LightLeft), h * 0.05f))
}

//cilindro de luz que va encima de la galleta, casi blanco arriba y del color abajo, asi la tine
fun DrawScope.drawCookieBeam(color: Color, strength: Float) {
    val w = size.width
    val h = size.height
    val left = w * LightLeft
    val right = w * LightRight
    val top = h * 0.44f
    val capTop = h * 0.685f
    val capBottom = h * 0.735f
    val glow = lerp(color, Color.White, 0.75f)
    val brush = Brush.verticalGradient(0f to glow.copy(alpha = 0f), 0.3f to lerp(color, Color.White, 0.5f).copy(alpha = 0.3f * strength), 0.7f to lerp(color, Color.White, 0.2f).copy(alpha = 0.62f * strength), 1f to color.copy(alpha = 0.86f * strength), startY = top, endY = capBottom)
    val beam = Path().apply {
        moveTo(left, top)
        lineTo(right, top)
        lineTo(right, (capTop + capBottom) / 2f)
        arcTo(Rect(left, capTop, right, capBottom), 0f, 180f, false)
        close()
    }
    drawPath(beam, brush)
}

//pedacitos de galleta que saltan hacia arriba al partirse y caen con la gravedad
fun DrawScope.drawCookieShards(progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val origin = Offset(size.width * 0.5f, size.height * 0.35f)
    for (i in 0 until 12) {
        val angle = (-175.0 + i * 15.0) * PI / 180.0
        val distance = size.width * 0.7f * progress * (0.7f + (i % 3) * 0.15f)
        val fall = size.height * 0.55f * progress * progress
        val x = origin.x + (cos(angle) * distance).toFloat()
        val y = origin.y + (sin(angle) * distance).toFloat() + fall
        val r = size.width * 0.022f * (1 + i % 3)
        val shard = Path()
        shard.moveTo(x, y - r)
        shard.lineTo(x + r, y + r)
        shard.lineTo(x - r * 0.8f, y + r * 0.6f)
        shard.close()
        drawPath(path = shard, color = if (i % 2 == 0) CookieCrumbLight else CookieCrumbDark, alpha = 1f - progress)
    }
}

//papelito que sale ondulado de la galleta y se va estirando hasta quedar plano
//progress va de 0 a 1, finalWidth y finalHeight son el tamano final del papel
fun DrawScope.drawPaperRibbon(progress: Float, finalWidth: Float, finalHeight: Float, center: Offset) {
    if (progress <= 0f) return
    val width = finalWidth * (0.15f + 0.85f * progress)
    val grow = ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f)
    val height = finalHeight * (0.18f + 0.82f * grow)
    //al salir ondula mucho y al final queda con una onda suave, como papel de verdad
    val wave = size.width * 0.05f * (1f - progress) + finalHeight * 0.035f * progress
    drawFortunePaper(center.x - width / 2f, center.y - height / 2f, width, height, wave)
}

//papelito de la galleta: bordes de arriba y abajo ondulados, esquinas redondas, sombra suave y un brillo en el medio
fun DrawScope.drawFortunePaper(left: Float, top: Float, width: Float, height: Float, wave: Float) {
    val path = fortunePaperPath(left, top, width, height, wave)
    //sombra corrida hacia abajo
    drawPath(path = fortunePaperPath(left, top + 4.dp.toPx(), width, height, wave), color = TextColor.copy(alpha = 0.16f))
    drawPath(path = path, brush = Brush.verticalGradient(listOf(FortunePaperColor, FortunePaperColor, lerp(FortunePaperColor, PrimaryColor, 0.1f)), startY = top, endY = top + height))
    drawPath(path = path, color = PrimaryColor.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
}

//camino del papelito, la onda es una sola curva suave de punta a punta
private fun fortunePaperPath(left: Float, top: Float, width: Float, height: Float, wave: Float): Path {
    val radius = minOf(32f, height / 4f, width / 8f)
    val steps = 28
    val path = Path()
    fun edgeY(base: Float, i: Int) = base + wave * sin(i * 2.0 * PI / steps).toFloat()
    val right = left + width
    path.moveTo(left + radius, edgeY(top, 1))
    for (i in 1..steps - 1) path.lineTo(left + width * i / steps, edgeY(top, i))
    path.quadraticBezierTo(right, edgeY(top, steps), right, top + radius)
    path.lineTo(right, top + height - radius)
    path.quadraticBezierTo(right, edgeY(top + height, steps), left + width * (steps - 1) / steps, edgeY(top + height, steps - 1))
    for (i in steps - 2 downTo 1) path.lineTo(left + width * i / steps, edgeY(top + height, i))
    path.quadraticBezierTo(left, edgeY(top + height, 0), left, top + height - radius)
    path.lineTo(left, top + radius)
    path.quadraticBezierTo(left, edgeY(top, 0), left + width / steps, edgeY(top, 1))
    path.close()
    return path
}
