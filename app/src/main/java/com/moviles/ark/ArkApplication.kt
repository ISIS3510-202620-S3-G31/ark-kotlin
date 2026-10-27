package com.moviles.ark

import android.app.Application
import com.moviles.ark.data.AppContainer
import com.moviles.ark.data.DefaultAppContainer
import com.moviles.ark.data.local.ProfilePreferences
import com.moviles.ark.data.remote.ToolsCatalogMessagingService

class ArkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        //inicializa las preferencias locales del usuario (offline-first)
        ProfilePreferences.init(this)
        //iniciar el observador de sincronizacion automatica offline
        container.syncManager.startMonitoring()
        //escuchar los avisos push de cambios en el catalogo de herramientas
        ToolsCatalogMessagingService.subscribeToCatalogUpdates()
    }
}
