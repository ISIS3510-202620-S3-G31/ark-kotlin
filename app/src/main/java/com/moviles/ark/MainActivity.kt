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
        setContent {
            //aplicamos el tema de la app y lanzamos la navegacion principal
            AppTheme({
                AppNavigation()
            })
        }
    }
}