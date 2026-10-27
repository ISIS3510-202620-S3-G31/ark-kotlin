package com.moviles.ark

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.moviles.ark.data.AppContainer
import com.moviles.ark.data.DefaultAppContainer
import com.moviles.ark.data.local.ProfilePreferences
import com.moviles.ark.data.remote.ToolsCatalogMessagingService
import com.moviles.ark.domain.models.ScreenTimeTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        //escuchar los avisos push de cambios en el catalogo de herramientas
        ToolsCatalogMessagingService.subscribeToCatalogUpdates()
        //los eventos de analitica llevan el uid de la cuenta para poder sumar el tiempo de pantalla por usuario
        container.auth.addAuthStateListener { auth -> container.analyticsRepository.setUserId(auth.currentUser?.uid) }
        //tiempo de pantalla: ProcessLifecycleOwner avisa cuando la app entera entra o sale de pantalla
        //(no cada actividad), y espera un momento antes de avisar la salida para que rotar el telefono no corte la sesion
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.screenTimeTracker.onAppForeground()
            override fun onStop(owner: LifecycleOwner) = container.screenTimeTracker.onAppBackground()
        })
        //mientras la app esta en pantalla se envia el tiempo cada minuto; al salir se detiene y al volver arranca de nuevo
        val processLifecycle = ProcessLifecycleOwner.get()
        processLifecycle.lifecycleScope.launch {
            processLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(ScreenTimeTracker.CHECKPOINT_INTERVAL_MS)
                    container.screenTimeTracker.onCheckpoint()
                }
            }
        }
    }

    companion object {
        private const val GLIDE_CACHE_DIR = "photo_glide_cache"
        private const val GLIDE_CACHE_BYTES = 50L * 1024 * 1024
    }
}
