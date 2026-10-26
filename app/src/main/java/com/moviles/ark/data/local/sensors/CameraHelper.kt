package com.moviles.ark.data.local.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.moviles.ark.data.remote.CrashlyticsHelper
import java.io.File

//helper de la camara para validar permisos y generar uris seguras para captura de fotos (#31, #146)
class CameraHelper(private val context: Context) {

    //verifica si el usuario ya otorgo el permiso de camara en el telefono
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    //crea un archivo seguro apuntando a filesDir/photos/ para la captura de fotos con FileProvider (#146)
    fun createPictureUri(): Uri? {
        return try {
            val photosDir = File(context.filesDir, "photos").apply {
                if (!exists()) mkdirs()
            }
            val photoFile = File.createTempFile(
                "photo_${System.currentTimeMillis()}_",
                ".jpg",
                photosDir
            )
            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, photoFile)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "CameraHelper",
                action = "createPictureUri",
                throwable = e
            )
            null
        }
    }

    //mantiene compatibilidad hacia atras
    fun createTempPictureUri(): Uri? = createPictureUri()
}
