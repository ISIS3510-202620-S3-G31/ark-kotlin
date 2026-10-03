package com.moviles.ark.data.local.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.moviles.ark.data.remote.CrashlyticsHelper
import java.io.File

//helper de la camara para validar permisos y generar uris seguras para captura de fotos (#31)
class CameraHelper(private val context: Context) {

    //verifica si el usuario ya otorgo el permiso de camara en el telefono
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    //crea un archivo temporal seguro para almacenar la foto capturada por la camara
    fun createTempPictureUri(): Uri? {
        return try {
            val storageDir = File(context.cacheDir, "camera").apply {
                if (!exists()) mkdirs()
            }
            val tempFile = File.createTempFile(
                "photo_${System.currentTimeMillis()}_",
                ".jpg",
                storageDir
            )
            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, tempFile)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "CameraHelper",
                action = "createTempPictureUri",
                throwable = e
            )
            null
        }
    }
}
