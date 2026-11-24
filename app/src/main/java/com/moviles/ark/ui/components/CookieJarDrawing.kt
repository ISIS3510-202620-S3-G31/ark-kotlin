package com.moviles.ark.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.moviles.ark.domain.models.CookieJar
import com.moviles.ark.domain.models.CookieModel
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor

//hueco fijo de una galleta dentro del jarro: centro en fraccion del ancho y alto del cuerpo, y una rotacion leve
private data class CookieSlot(val x: Float, val y: Float, val rotation: Float)

//10 huecos en filas de 3-2-3-2 desde el fondo; la galleta i siempre va en el hueco i, asi el dibujo es estable
//el tamano de la galleta se calcula para que con 10 el jarro se vea lleno; con menos se ve mas vacio
private val SLOTS = listOf(
    CookieSlot(0.19f, 0.86f, -8f), CookieSlot(0.50f, 0.88f, 6f), CookieSlot(0.81f, 0.86f, -4f),
    CookieSlot(0.34f, 0.64f, 10f), CookieSlot(0.66f, 0.65f, -7f),
    CookieSlot(0.19f, 0.43f, 4f), CookieSlot(0.50f, 0.44f, -10f), CookieSlot(0.81f, 0.43f, 7f),
    CookieSlot(0.34f, 0.22f, -5f), CookieSlot(0.66f, 0.22f, 9f),
)

//diametro de la galleta como fraccion del ancho del cuerpo del jarro
private const val COOKIE_SIZE_FRACTION = 0.34f

//jarro de vidrio dibujado a mano (canvas, sin imagenes) con las galletas del dia; tocar una galleta la abre
@Composable
fun CookieJarDrawing(
    cookies: List<CookieModel>,
    onCookieClick: (CookieModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.aspectRatio(JAR_ASPECT)) {
        val jarWidth = maxWidth
        val jarHeight = maxHeight
        Canvas(modifier = Modifier.fillMaxSize()) { drawJar() }

        //cuerpo del jarro (donde van las galletas), en las mismas proporciones que drawJar
        val bodyLeft = jarWidth * BODY_INSET_X
        val bodyWidth = jarWidth * (1f - 2 * BODY_INSET_X)
        val bodyTop = jarHeight * BODY_TOP
        val bodyHeight = jarHeight * (BODY_BOTTOM - BODY_TOP)
        val cookieSize: Dp = bodyWidth * COOKIE_SIZE_FRACTION

        cookies.take(CookieJar.CAPACITY).forEachIndexed { index, cookie ->
            val slot = SLOTS[index]
            Box(
                modifier = Modifier
                    .offset(
                        x = bodyLeft + bodyWidth * slot.x - cookieSize / 2,
                        y = bodyTop + bodyHeight * slot.y - cookieSize / 2
                    )
                    .size(cookieSize)
                    .rotate(slot.rotation)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Open") { onCookieClick(cookie) }
                    .semantics { contentDescription = cookie.title }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) { drawCookie() }
            }
        }
    }
}

//proporciones del jarro: ancho / alto, y donde empieza y termina el cuerpo
private const val JAR_ASPECT = 0.78f
private const val BODY_INSET_X = 0.07f
private const val BODY_TOP = 0.20f
private const val BODY_BOTTOM = 0.95f

//vidrio con fondo claro semitransparente, borde redondeado, cuello y tapa en secondary
private fun DrawScope.drawJar() {
    val w = size.width
    val h = size.height
    val stroke = w * 0.018f
    val outline = TextColor.copy(alpha = 0.55f)

    //tapa
    val lidWidth = w * 0.62f
    val lidLeft = (w - lidWidth) / 2
    drawRoundRect(
        color = SecondaryColor,
        topLeft = Offset(lidLeft, h * 0.02f),
        size = Size(lidWidth, h * 0.075f),
        cornerRadius = CornerRadius(w * 0.03f)
    )
    drawRoundRect(
        color = outline,
        topLeft = Offset(lidLeft, h * 0.02f),
        size = Size(lidWidth, h * 0.075f),
        cornerRadius = CornerRadius(w * 0.03f),
        style = Stroke(width = stroke)
    )

    //vidrio: cuello angosto que se abre en el cuerpo
    val neckWidth = w * 0.54f
    val neckLeft = (w - neckWidth) / 2
    val glass = Path().apply {
        moveTo(neckLeft, h * 0.10f)
        lineTo(neckLeft, h * 0.13f)
        cubicTo(neckLeft, h * 0.16f, w * 0.02f, h * 0.15f, w * 0.02f, h * 0.22f)
        lineTo(w * 0.02f, h * 0.90f)
        quadraticTo(w * 0.02f, h * 0.985f, w * 0.10f, h * 0.985f)
        lineTo(w * 0.90f, h * 0.985f)
        quadraticTo(w * 0.98f, h * 0.985f, w * 0.98f, h * 0.90f)
        lineTo(w * 0.98f, h * 0.22f)
        cubicTo(w * 0.98f, h * 0.15f, neckLeft + neckWidth, h * 0.16f, neckLeft + neckWidth, h * 0.13f)
        lineTo(neckLeft + neckWidth, h * 0.10f)
        close()
    }
    drawPath(glass, color = BackgroundLightColor.copy(alpha = 0.65f))
    drawPath(glass, color = outline, style = Stroke(width = stroke))

    //brillo del vidrio a la izquierda
    drawRoundRect(
        color = BackgroundLightColor,
        topLeft = Offset(w * 0.08f, h * 0.27f),
        size = Size(w * 0.035f, h * 0.45f),
        cornerRadius = CornerRadius(w * 0.02f)
    )
}

//galleta redonda en primary con borde y chispas de chocolate en el color del texto
private fun DrawScope.drawCookie() {
    val r = size.minDimension / 2
    val center = Offset(size.width / 2, size.height / 2)
    drawCircle(color = PrimaryColor, radius = r, center = center)
    drawCircle(color = TextColor.copy(alpha = 0.25f), radius = r * 0.94f, center = center, style = Stroke(width = r * 0.08f))
    drawCircle(color = BackgroundColor.copy(alpha = 0.35f), radius = r * 0.62f, center = center + Offset(-r * 0.12f, -r * 0.12f))
    val chips = listOf(
        Offset(-0.35f, -0.30f), Offset(0.25f, -0.42f), Offset(0.40f, 0.10f),
        Offset(-0.10f, 0.05f), Offset(-0.42f, 0.30f), Offset(0.12f, 0.45f),
    )
    chips.forEachIndexed { i, chip ->
        drawCircle(
            color = TextColor.copy(alpha = 0.8f),
            radius = r * (if (i % 2 == 0) 0.11f else 0.085f),
            center = center + Offset(chip.x * r, chip.y * r)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9DEAA)
@Composable
private fun CookieJarDrawingPreview() {
    AppTheme {
        CookieJarDrawing(
            cookies = (1..10).map { CookieModel(id = "$it", title = "Cookie $it", text = "") },
            onCookieClick = {}
        )
    }
}
