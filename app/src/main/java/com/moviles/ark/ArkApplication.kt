package com.moviles.ark

import android.app.Application
import com.moviles.ark.data.AppContainer
import com.moviles.ark.data.DefaultAppContainer

class ArkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
