package com.moviles.ark.data.local

import android.content.ContentValues
import android.content.Context
import android.content.Intent
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
import com.moviles.ark.domain.models.FortuneModel
import java.io.OutputStream

//exportador que dibuja la tarjeta de la fortuna como bitmap y la guarda en la galeria publica
class FortuneCardExporter(private val context: Context) {

    //renderiza la tarjeta visualmente en un mapa de bits de alta resolucion
    fun renderCardBitmap(fortune: FortuneModel): Bitmap {
        val width = 1080
        val height = 1080
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        //color de fondo calido de la aplicacion
        canvas.drawColor(Color.parseColor("#FFF5E6"))

        //dibujamos la tarjeta central con esquinas redondeadas
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val cardRect = RectF(60f, 60f, (width - 60).toFloat(), (height - 60).toFloat())
        canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

        //borde decorativo sutil
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8840E")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, borderPaint)

        //titulo superior
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8840E")
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("FORTUNE COOKIE WISDOM", width / 2f, 180f, titlePaint)

        //categoria
        val catPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#888888")
            textSize = 36f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Category: ${fortune.category}", width / 2f, 240f, catPaint)

        //frase central con ajuste automatico de lineas
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#221100")
            textSize = 52f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        val textWidth = width - 240
        val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain("\"${fortune.quote}\"", 0, fortune.quote.length + 2, textPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(12f, 1f)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout("\"${fortune.quote}\"", textPaint, textWidth, Layout.Alignment.ALIGN_CENTER, 1f, 12f, false)
        }

        canvas.save()
        val textY = (height / 2f) - (staticLayout.height / 2f)
        canvas.translate(120f, textY)
        staticLayout.draw(canvas)
        canvas.restore()

        //numero de la suerte
        val luckyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2BA79B")
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Lucky Number: ${fortune.luckyNumber}", width / 2f, (height - 180).toFloat(), luckyPaint)

        //firma de la aplicacion
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#AAAAAA")
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Shared from Ark App", width / 2f, (height - 110).toFloat(), footerPaint)

        return bitmap
    }

    //guarda el bitmap en mediastore en la carpeta publica pictures
    fun saveCardToGallery(bitmap: Bitmap, title: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "fortune_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Ark")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
        resolver.openOutputStream(uri)?.use { stream: OutputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        return uri
    }

    //lanza el selector del sistema para compartir la tarjeta por cualquier aplicacion
    fun shareFortuneCard(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share your fortune card").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
