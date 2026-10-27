package com.moviles.ark

import android.app.Application
import com.moviles.ark.data.AppContainer
import com.moviles.ark.data.DefaultAppContainer
import com.moviles.ark.data.local.AvatarPreferences
import com.moviles.ark.data.remote.ToolsCatalogMessagingService

class ArkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        AvatarPreferences.init(this)
        //iniciar el observador de sincronizacion automatica offline (#33)
        container.syncManager.startMonitoring()
        //precargar y verificar el soporte del modelo de reconocimiento de voz offline
        container.speechToTextHelper.preloadOfflineModel()
        //escuchar los avisos push de cambios en el catalogo de herramientas (#156)
        ToolsCatalogMessagingService.subscribeToCatalogUpdates()
    }
}
