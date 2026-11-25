package com.moviles.ark.ui.screens

import com.moviles.ark.ui.theme.BackgroundLightColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.random.Random

//colores del paisaje de noche
private val SkyTopColor = Color(0xFF0B1033)
private val SkyMiddleColor = Color(0xFF2B1F5C)
private val SkyLowColor = Color(0xFF7A3E6E)
private val SkyHorizonColor = Color(0xFFF3A65A)
private val MoonColor = Color(0xFFFFF4D6)
private val FarMountainColor = Color(0xFF3A2A5E)
private val NearHillColor = Color(0xFF1C1535)
private val LakeTopColor = Color(0xFF3A2C63)
private val LakeBottomColor = Color(0xFF0F0C22)

//donde termina el cielo y empieza el lago, como fraccion del alto de la pantalla
private const val HORIZON = 0.74f

//cuantas estrellas tiene el cielo
private const val STAR_COUNT = 70

//una estrella: su lugar como fraccion del ancho y del alto, y su tamano en dp
private data class Star(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    //la mitad de las estrellas brilla cuando la otra mitad se apaga
    val twinklesFirst: Boolean
)

//paisaje de noche para el fondo del cielo: estrellas, luna, montanas, colinas y un lago
@Composable
fun NightLandscape(modifier: Modifier = Modifier) {
    //las estrellas se crean una sola vez para que no cambien de lugar
    val stars = remember { createStars() }

    //las estrellas titilan: este numero sube y baja sin parar
    val twinkleTransition = rememberInfiniteTransition(label = "stars")
    val twinkle by twinkleTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1800), repeatMode = RepeatMode.Reverse),
        label = "twinkle"
    )

    Canvas(modifier = modifier) {
        //se dibuja por capas, de atras hacia adelante
        drawSky()
        drawStars(stars, twinkle)
        drawMoon()
        drawFarMountains()
        drawLake()
        drawNearHills()
    }
}

//crea estrellas en la parte de arriba del cielo
//siempre usa la misma semilla (7), asi las estrellas quedan en el mismo lugar cada vez
private fun createStars(): List<Star> {
    val random = Random(7)
    val stars = mutableListOf<Star>()
    for (index in 0 until STAR_COUNT) {
        val star = Star(
            x = random.nextFloat(),
            y = random.nextFloat() * 0.6f,
            radiusDp = 0.6f + random.nextFloat() * 1.4f,
            twinklesFirst = index % 2 == 0
        )
        stars.add(star)
    }
    return stars
}

//cielo: azul oscuro arriba, morado en el medio y naranja en el horizonte
private fun DrawScope.drawSky() {
    val skyColors = Brush.verticalGradient(
        0f to SkyTopColor,
        0.45f to SkyMiddleColor,
        0.65f to SkyLowColor,
        HORIZON to SkyHorizonColor
    )
    drawRect(brush = skyColors)
}

//estrellas: una mitad brilla mientras la otra se apaga
private fun DrawScope.drawStars(stars: List<Star>, twinkle: Float) {
    for (star in stars) {
        var brightness = twinkle
        if (!star.twinklesFirst) {
            brightness = 1.3f - twinkle
        }
        drawCircle(
            color = BackgroundLightColor.copy(alpha = brightness),
            radius = star.radiusDp.dp.toPx(),
            center = Offset(size.width * star.x, size.height * star.y)
        )
    }
}

//luna llena con dos circulos de luz alrededor
private fun DrawScope.drawMoon() {
    val center = Offset(size.width * 0.8f, size.height * 0.14f)
    val radius = size.width * 0.06f
    drawCircle(color = MoonColor.copy(alpha = 0.08f), radius = radius * 3f, center = center)
    drawCircle(color = MoonColor.copy(alpha = 0.15f), radius = radius * 1.8f, center = center)
    drawCircle(color = MoonColor, radius = radius, center = center)
}

//montanas lejanas sobre el horizonte
private fun DrawScope.drawFarMountains() {
    val width = size.width
    val height = size.height
    val horizonY = height * HORIZON

    val mountains = Path()
    mountains.moveTo(0f, horizonY)
    mountains.lineTo(0f, height * 0.66f)
    mountains.lineTo(width * 0.12f, height * 0.6f)
    mountains.lineTo(width * 0.25f, height * 0.65f)
    mountains.lineTo(width * 0.38f, height * 0.56f)
    mountains.lineTo(width * 0.52f, height * 0.64f)
    mountains.lineTo(width * 0.66f, height * 0.58f)
    mountains.lineTo(width * 0.8f, height * 0.63f)
    mountains.lineTo(width * 0.92f, height * 0.57f)
    mountains.lineTo(width, height * 0.62f)
    mountains.lineTo(width, horizonY)
    mountains.close()
    drawPath(path = mountains, color = FarMountainColor)
}

//lago debajo del horizonte, con el reflejo de la luna
private fun DrawScope.drawLake() {
    val width = size.width
    val height = size.height
    val horizonY = height * HORIZON

    val water = Brush.verticalGradient(
        colors = listOf(LakeTopColor, LakeBottomColor),
        startY = horizonY,
        endY = height
    )
    drawRect(brush = water, topLeft = Offset(0f, horizonY))

    //linea de luz naranja donde el cielo toca el agua
    drawLine(
        color = SkyHorizonColor.copy(alpha = 0.5f),
        start = Offset(0f, horizonY),
        end = Offset(width, horizonY),
        strokeWidth = 2.dp.toPx()
    )

    //reflejo de la luna: rayitas cada vez mas cortas y suaves
    val moonX = width * 0.8f
    for (index in 0 until 5) {
        val lineY = horizonY + height * 0.02f * (index + 1)
        val halfLength = width * 0.06f * (1f - index * 0.15f)
        drawLine(
            color = MoonColor.copy(alpha = 0.35f - index * 0.06f),
            start = Offset(moonX - halfLength, lineY),
            end = Offset(moonX + halfLength, lineY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

//colinas oscuras a los lados del lago, con unos pinos
private fun DrawScope.drawNearHills() {
    val width = size.width
    val height = size.height
    val horizonY = height * HORIZON

    //colina de la izquierda
    val leftHill = Path()
    leftHill.moveTo(0f, height * 0.67f)
    leftHill.cubicTo(width * 0.12f, height * 0.65f, width * 0.25f, height * 0.7f, width * 0.34f, horizonY)
    leftHill.lineTo(0f, horizonY)
    leftHill.close()
    drawPath(path = leftHill, color = NearHillColor)

    //colina de la derecha
    val rightHill = Path()
    rightHill.moveTo(width, height * 0.69f)
    rightHill.cubicTo(width * 0.9f, height * 0.68f, width * 0.8f, height * 0.71f, width * 0.72f, horizonY)
    rightHill.lineTo(width, horizonY)
    rightHill.close()
    drawPath(path = rightHill, color = NearHillColor)

    //pinos sobre la colina de la izquierda
    drawPineTree(baseX = width * 0.06f, baseY = height * 0.675f, treeHeight = height * 0.05f)
    drawPineTree(baseX = width * 0.12f, baseY = height * 0.67f, treeHeight = height * 0.065f)
    drawPineTree(baseX = width * 0.19f, baseY = height * 0.68f, treeHeight = height * 0.045f)
    //y uno sobre la de la derecha
    drawPineTree(baseX = width * 0.92f, baseY = height * 0.69f, treeHeight = height * 0.055f)

    //orilla de abajo, para que el farol grande quede sobre tierra
    val shore = Path()
    shore.moveTo(0f, height)
    shore.lineTo(0f, height * 0.93f)
    shore.cubicTo(width * 0.3f, height * 0.9f, width * 0.7f, height * 0.95f, width, height * 0.92f)
    shore.lineTo(width, height)
    shore.close()
    drawPath(path = shore, color = NearHillColor)
}

//un pino: un triangulo delgado parado en (baseX, baseY)
private fun DrawScope.drawPineTree(baseX: Float, baseY: Float, treeHeight: Float) {
    val halfWidth = treeHeight * 0.3f
    val tree = Path()
    tree.moveTo(baseX, baseY - treeHeight)
    tree.lineTo(baseX + halfWidth, baseY)
    tree.lineTo(baseX - halfWidth, baseY)
    tree.close()
    drawPath(path = tree, color = NearHillColor)
}
