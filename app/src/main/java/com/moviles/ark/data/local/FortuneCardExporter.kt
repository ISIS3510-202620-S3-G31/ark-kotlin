package com.moviles.ark.data.local

import java.io.File
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.moviles.ark.R
import com.moviles.ark.domain.models.FortuneModel
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//dibuja la frase como imagen vertical y la guarda en la galeria del telefono
class FortuneCardExporter(private val context: Context) {

    //colores de la paleta de la app
    private val textColor = Color.parseColor("#221100")
    private val backgroundLight = Color.parseColor("#FCEFD5")
    private val orange = Color.parseColor("#F8840E")
    private val coral = Color.parseColor("#EB5249")
    private val paperTop = Color.parseColor("#FBEACB")
    private val paperBottom = Color.parseColor("#FAE3B8")

    //imagen de 1080 x 1350 con fondo naranja a coral, la galleta abierta con el papelito en la mitad y la firma abajo
    fun renderCardBitmap(fortune: FortuneModel, savedAt: Long = System.currentTimeMillis()): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val sorean = loadFont(R.font.sorean)
        val figtree = loadFont(R.font.figtree_normal)

        //fondo en diagonal del naranja al coral de la paleta
        val backgroundPaint = Paint().apply { shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), orange, coral, Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        //luz crema detras de la galleta y del papel
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(width / 2f, height * 0.47f, width * 0.75f, Color.argb(130, 252, 239, 213), Color.TRANSPARENT, Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)
        //circulos grandes y suaves en las esquinas para darle profundidad
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 252, 239, 213) }
        canvas.drawCircle(-60f, 180f, 280f, bubblePaint)
        canvas.drawCircle(width + 40f, height - 260f, 340f, bubblePaint)
        //chispitas crema alrededor de la galleta
        val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 252, 239, 213) }
        val sparkles = floatArrayOf(150f, 200f, 8f, 930f, 170f, 7f, 120f, 1000f, 6f, 960f, 1060f, 9f, 300f, 140f, 5f, 780f, 260f, 6f, 540f, 170f, 7f)
        var i = 0
        while (i + 2 < sparkles.size) {
            canvas.drawCircle(sparkles[i], sparkles[i + 1], sparkles[i + 2], sparklePaint)
            i += 3
        }

        //el papelito en la mitad con la frase, la fecha debajo, el papel se ajusta al alto del texto
        val centerY = 640f
        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; textSize = 50f; typeface = sorean }
        val paperWidth = 860f
        val textWidth = 500
        val layout = StaticLayout.Builder.obtain(fortune.quote, 0, fortune.quote.length, quotePaint, textWidth).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(8f, 1f).build()
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 34, 17, 0); textSize = 28f; typeface = figtree; textAlign = Paint.Align.CENTER }
        val date = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).format(Date(savedAt))
        val paperHeight = 70f + layout.height + 26f + 5f + 50f + 50f
        val paper = RectF(width / 2f - paperWidth / 2f, centerY - paperHeight / 2f, width / 2f + paperWidth / 2f, centerY + paperHeight / 2f)

        //papel un poco torcido con su sombra, como recien sacado de la galleta
        canvas.save()
        canvas.rotate(-2f, width / 2f, centerY)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paperTop; setShadowLayer(44f, 0f, 20f, Color.argb(80, 34, 17, 0)) }
        canvas.drawRoundRect(paper, 10f, 10f, shadowPaint)
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, paper.top, 0f, paper.bottom, Color.parseColor("#FFF8EA"), paperBottom, Shader.TileMode.CLAMP) }
        canvas.drawRoundRect(paper, 10f, 10f, paperPaint)
        canvas.save()
        canvas.translate(width / 2f - textWidth / 2f, paper.top + 70f)
        layout.draw(canvas)
        canvas.restore()
        val ruleTop = paper.top + 70f + layout.height + 26f
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = orange }
        canvas.drawRoundRect(RectF(width / 2f - 26f, ruleTop, width / 2f + 26f, ruleTop + 5f), 3f, 3f, rulePaint)
        canvas.drawText(date, width / 2f, ruleTop + 52f, datePaint)
        canvas.restore()

        //las dos mitades de la galleta abiertas tapando las puntas del papel
        drawCookieHalf(canvas, R.drawable.fortune_cookie_left, paper.left + 20f, centerY, -1f)
        drawCookieHalf(canvas, R.drawable.fortune_cookie_right, paper.right - 20f, centerY, 1f)

        //firma de la app abajo en crema
        val signaturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundLight; textSize = 64f; typeface = sorean; textAlign = Paint.Align.CENTER }
        canvas.drawText("Ark", width / 2f, height - 110f, signaturePaint)
        val taglinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 252, 239, 213); textSize = 30f; typeface = figtree; textAlign = Paint.Align.CENTER }
        canvas.drawText("my fortune cookie of the day", width / 2f, height - 60f, taglinePaint)
        return bitmap
    }

    //una mitad con el centro en x, y inclinada hacia afuera, con sombra en el piso; si la imagen no carga no dibuja nada
    private fun drawCookieHalf(canvas: Canvas, id: Int, x: Float, y: Float, side: Float) {
        val half = try { BitmapFactory.decodeResource(context.resources, id) } catch (_: Exception) { null } ?: return
        val size = 420f
        //donde queda el centro de la galleta dentro de la imagen de cada mitad
        val contentX = if (side < 0f) 0.30f else 0.63f
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(-side * 16f)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 34, 17, 0); maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL) }
        canvas.drawOval(RectF(-size * 0.22f, size * 0.36f, size * 0.22f, size * 0.48f), shadowPaint)
        canvas.drawBitmap(half, null, RectF(-contentX * size, -0.5f * size, (1f - contentX) * size, 0.5f * size), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        half.recycle()
    }

    //carga una fuente de res/font, si falla usa la del sistema
    private fun loadFont(id: Int): Typeface {
        return try { ResourcesCompat.getFont(context, id) ?: Typeface.DEFAULT } catch (_: Exception) { Typeface.DEFAULT }
    }

    //convierte la imagen en bytes png para subirla a la nube
    fun toPngBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    //guarda la imagen en la carpeta pictures/ark de la galeria
    fun saveCardToGallery(bitmap: Bitmap): Uri? {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "fortune_${System.currentTimeMillis()}.png")
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Ark")
                values.put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (_: Exception) {
            null
        }
    }

    //revisa si la imagen sigue en la galeria, por si la borraron
    fun exists(uri: String): Boolean {
        if (!uri.startsWith("content://")) return false
        return try { context.contentResolver.openInputStream(Uri.parse(uri))?.use { true } ?: false } catch (_: Exception) { false }
    }
}
