package com.moviles.ark

import android.app.Application
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.moviles.ark.data.AppContainer
import com.moviles.ark.data.DefaultAppContainer
import com.moviles.ark.data.local.ProfilePreferences
import com.moviles.ark.data.remote.ToolsCatalogMessagingService

class ArkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        //cache en disco de glide en cacheDir con tope de 50 MB: las fotos que se bajan de storage se ven sin red
        //y no se vuelven a descargar con datos; android puede vaciarla si le falta espacio
        Glide.init(
            this,
            GlideBuilder().setDiskCache(InternalCacheDiskCacheFactory(this, GLIDE_CACHE_DIR, GLIDE_CACHE_BYTES))
        )
        container = DefaultAppContainer(this)
        //inicializa las preferencias locales del usuario (perfil y avatar) para cargarlas al instante y sin red
        ProfilePreferences.init(this)
        //iniciar el observador de sincronizacion automatica offline
        container.syncManager.startMonitoring()
        //precargar y verificar el soporte del modelo de reconocimiento de voz offline
        container.speechToTextHelper.preloadOfflineModel()
        //escuchar los avisos push de cambios en el catalogo de herramientas (#156)
        ToolsCatalogMessagingService.subscribeToCatalogUpdates()
    }

    companion object {
        private const val GLIDE_CACHE_DIR = "photo_glide_cache"
        private const val GLIDE_CACHE_BYTES = 50L * 1024 * 1024
    }
}
