package com.moviles.ark

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.moviles.ark.ui.navigation.AppNavigation
import com.moviles.ark.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme({
                AppNavigation()
            })
        }
    }

    //el paquete de voz offline se pide con la app en pantalla: android no deja conectarse al reconocedor
    //del telefono desde el arranque en segundo plano (respondia "not connected to the recognition service")
    override fun onResume() {
        super.onResume()
        (application as ArkApplication).container.speechToTextHelper.preloadOfflineModel()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
