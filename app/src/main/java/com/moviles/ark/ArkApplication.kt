package com.moviles.ark

import android.app.Application
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.moviles.ark.data.AppContainer
import com.moviles.ark.data.DefaultAppContainer
import com.moviles.ark.data.local.AvatarPreferences
import com.moviles.ark.data.remote.ToolsCatalogMessagingService

class ArkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (!Glide.isInitialized()) {
            Glide.init(
                this,
                GlideBuilder().setDiskCache(
                    InternalCacheDiskCacheFactory(this, "photo_glide_cache", 50L * 1024 * 1024)
                )
            )
        }
        container = DefaultAppContainer(this)
        AvatarPreferences.init(this)
        //iniciar el observador de sincronizacion automatica offline (#33)
        container.syncManager.startMonitoring()
        //escuchar los avisos push de cambios en el catalogo de herramientas (#156)
        ToolsCatalogMessagingService.subscribeToCatalogUpdates()
    }
}
