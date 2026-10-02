package com.moviles.ark

import android.app.Application
import com.moviles.ark.data.AppContainer
import com.moviles.ark.data.DefaultAppContainer

// Runs before any screen. Creates the container once so every ViewModel shares the same instances.
class ArkApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer()
    }
}
