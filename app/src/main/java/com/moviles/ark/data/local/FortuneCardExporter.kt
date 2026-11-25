package com.moviles.ark.data.local

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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

//dibuja la frase como imagen y la guarda en la galeria del telefono
class FortuneCardExporter(private val context: Context) {

    //dibuja una imagen cuadrada con los colores y las letras de la app, el bloque de texto queda centrado
    fun renderCardBitmap(fortune: FortuneModel, savedAt: Long = System.currentTimeMillis()): Bitmap {
        val size = 1080
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val sorean = loadFont(R.font.sorean)
        val figtree = loadFont(R.font.figtree_normal)
        val dark = Color.parseColor("#221100")

        //los tres textos: titulo, frase y fecha
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dark; textSize = 56f; typeface = sorean; textAlign = Paint.Align.CENTER }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = dark; textSize = 48f; typeface = figtree }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8A5A2B"); textSize = 32f; typeface = figtree; textAlign = Paint.Align.CENTER }
        val layout = StaticLayout.Builder.obtain(fortune.quote, 0, fortune.quote.length, textPaint, size - 340).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(6f, 1f).build()
        val date = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).format(Date(savedAt))

        //alto del bloque para centrarlo y que el papel no quede con huecos
        val gap = 44f
        val blockHeight = 56f + gap + layout.height + gap + 32f
        val top = size / 2f - blockHeight / 2f

        //fondo de la paleta y el papelito justo del tamano del bloque
        canvas.drawColor(Color.parseColor("#F9DEAA"))
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FAE5BB") }
        val paper = RectF(110f, top - 90f, size - 110f, top + blockHeight + 90f)
        canvas.drawRoundRect(paper, 14f, 14f, paperPaint)

        //dos lineas finas como en los papelitos de verdad
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#59F8840E"); strokeWidth = 3f }
        canvas.drawLine(paper.left + 36f, paper.top + 36f, paper.right - 36f, paper.top + 36f, linePaint)
        canvas.drawLine(paper.left + 36f, paper.bottom - 36f, paper.right - 36f, paper.bottom - 36f, linePaint)

        //titulo, frase y fecha uno debajo del otro
        canvas.drawText("My fortune", size / 2f, top + 50f, titlePaint)
        canvas.save()
        canvas.translate(170f, top + 56f + gap)
        layout.draw(canvas)
        canvas.restore()
        canvas.drawText(date, size / 2f, top + blockHeight - 4f, datePaint)

        //firma de la app debajo del papel
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2BA79B"); textSize = 32f; typeface = figtree; textAlign = Paint.Align.CENTER }
        canvas.drawText("Ark", size / 2f, paper.bottom + 70f, footerPaint)
        return bitmap
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
