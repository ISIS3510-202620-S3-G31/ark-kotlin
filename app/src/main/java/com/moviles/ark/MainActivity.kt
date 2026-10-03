package com.moviles.ark

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
        val appContainer = (application as ArkApplication).container
        setContent {
            AppTheme({
                AppNavigation(authRepository = appContainer.authRepository)
            })
        }
    }
}