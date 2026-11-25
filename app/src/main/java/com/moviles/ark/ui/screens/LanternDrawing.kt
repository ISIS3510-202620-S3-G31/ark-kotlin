package com.moviles.ark.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

//colores del farol de papel encendido: blanco calido en el centro y naranja en los bordes
private val LitCenterColor = Color(0xFFFFF6D6)
private val LitMiddleColor = Color(0xFFFFC163)
private val LitEdgeColor = Color(0xFFE8711C)
//colores del farol apagado: papel sin luz
private val UnlitCenterColor = Color(0xFFC9AE92)
private val UnlitEdgeColor = Color(0xFF7D6150)
//aro de madera de arriba y de abajo
private val RimColor = Color(0xFF6B3410)
//llama de la vela
private val FlameColor = Color(0xFFFFFBE8)

//dibujo de un farol de papel redondeado
//encendido: brilla, tiene resplandor y una llama que titila; apagado: papel oscuro sin luz
//el dibujo se adapta al tamano que le den; se ve mejor si el alto es 1.25 veces el ancho
@Composable
fun LanternDrawing(isLit: Boolean, modifier: Modifier = Modifier) {
    //la luz titila: este numero sube y baja sin parar entre 0.85 y 1
    val flickerTransition = rememberInfiniteTransition(label = "lanternFlicker")
    val flicker by flickerTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 700), repeatMode = RepeatMode.Reverse),
        label = "flicker"
    )

    Canvas(modifier = modifier) {
        //se dibuja por capas, de atras hacia adelante
        if (isLit) {
            drawLanternGlow(flicker)
        }
        drawLanternBody(isLit)
        drawLanternRibs(isLit)
        drawLanternRims()
        if (isLit) {
            drawLanternFlame(flicker)
        }
    }
}

//resplandor suave alrededor del farol encendido
private fun DrawScope.drawLanternGlow(flicker: Float) {
    val center = Offset(size.width / 2, size.height * 0.55f)
    val radius = size.width * 1.1f
    val glow = Brush.radialGradient(
        colors = listOf(LitMiddleColor.copy(alpha = 0.55f * flicker), Color.Transparent),
        center = center,
        radius = radius
    )
    drawCircle(brush = glow, radius = radius, center = center)
}

//forma del farol: lados redondeados, un poco mas ancho arriba que abajo
private fun lanternShape(width: Float, height: Float): Path {
    val shape = Path()
    //esquina de arriba a la izquierda
    shape.moveTo(width * 0.2f, height * 0.12f)
    //borde de arriba
    shape.lineTo(width * 0.8f, height * 0.12f)
    //lado derecho, curvo hacia afuera
    shape.cubicTo(width * 0.98f, height * 0.3f, width * 0.96f, height * 0.7f, width * 0.72f, height * 0.88f)
    //borde de abajo
    shape.lineTo(width * 0.28f, height * 0.88f)
    //lado izquierdo, curvo hacia afuera
    shape.cubicTo(width * 0.04f, height * 0.7f, width * 0.02f, height * 0.3f, width * 0.2f, height * 0.12f)
    shape.close()
    return shape
}

//el papel del farol; encendido la luz es mas fuerte abajo, donde esta la vela
private fun DrawScope.drawLanternBody(isLit: Boolean) {
    var paper = Brush.radialGradient(
        colors = listOf(UnlitCenterColor, UnlitEdgeColor),
        center = Offset(size.width / 2, size.height * 0.6f),
        radius = size.height * 0.6f
    )
    if (isLit) {
        paper = Brush.radialGradient(
            colors = listOf(LitCenterColor, LitMiddleColor, LitEdgeColor),
            center = Offset(size.width / 2, size.height * 0.75f),
            radius = size.height * 0.8f
        )
    }
    drawPath(path = lanternShape(size.width, size.height), brush = paper)
}

//varillas del farol: dos lineas curvas de arriba a abajo, como los dobleces del papel
private fun DrawScope.drawLanternRibs(isLit: Boolean) {
    val width = size.width
    val height = size.height

    var ribColor = RimColor.copy(alpha = 0.25f)
    if (isLit) {
        ribColor = LitEdgeColor.copy(alpha = 0.35f)
    }
    val ribStroke = Stroke(width = width * 0.025f)

    val leftRib = Path()
    leftRib.moveTo(width * 0.38f, height * 0.13f)
    leftRib.cubicTo(width * 0.3f, height * 0.4f, width * 0.32f, height * 0.65f, width * 0.42f, height * 0.87f)
    drawPath(path = leftRib, color = ribColor, style = ribStroke)

    val rightRib = Path()
    rightRib.moveTo(width * 0.62f, height * 0.13f)
    rightRib.cubicTo(width * 0.7f, height * 0.4f, width * 0.68f, height * 0.65f, width * 0.58f, height * 0.87f)
    drawPath(path = rightRib, color = ribColor, style = ribStroke)
}

//aros de madera: uno arriba y otro abajo
private fun DrawScope.drawLanternRims() {
    val width = size.width
    val height = size.height
    val roundCorners = CornerRadius(width * 0.03f)

    drawRoundRect(
        color = RimColor,
        topLeft = Offset(width * 0.17f, height * 0.08f),
        size = Size(width * 0.66f, height * 0.06f),
        cornerRadius = roundCorners
    )
    drawRoundRect(
        color = RimColor,
        topLeft = Offset(width * 0.26f, height * 0.86f),
        size = Size(width * 0.48f, height * 0.05f),
        cornerRadius = roundCorners
    )
}

//llama de la vela, abajo dentro del farol
private fun DrawScope.drawLanternFlame(flicker: Float) {
    val width = size.width
    val height = size.height
    val flameWidth = width * 0.14f * flicker
    val flameHeight = height * 0.16f * flicker
    val flameCenterX = width / 2
    val flameBottomY = height * 0.85f

    //luz alrededor de la llama
    drawCircle(
        color = FlameColor.copy(alpha = 0.5f),
        radius = flameWidth,
        center = Offset(flameCenterX, flameBottomY - flameHeight / 2)
    )
    //la llama
    drawOval(
        color = FlameColor,
        topLeft = Offset(flameCenterX - flameWidth / 2, flameBottomY - flameHeight),
        size = Size(flameWidth, flameHeight)
    )
    //mecha debajo de la llama
    drawLine(
        color = RimColor,
        start = Offset(flameCenterX, flameBottomY),
        end = Offset(flameCenterX, flameBottomY + height * 0.02f),
        strokeWidth = width * 0.02f,
        cap = StrokeCap.Round
    )
}
