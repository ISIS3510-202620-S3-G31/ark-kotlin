package com.moviles.ark.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.moviles.ark.ui.screens.LoginRoute

@Composable
fun AppNavigation() {
    //  El controlador que gestiona el historial de pantallas
    val navController = rememberNavController()

    //  El contenedor que define qué pantalla mostrar según la ruta
    NavHost(
        navController = navController,
        startDestination = "login_screen" //  Ruta inicial TODO no se si la  vamos a dejar asi
    ) {
        // Ruta de Login
        composable("login_screen") {
            LoginRoute(
                onNavigateToRegister = {
                    navController.navigate("register_screen")
                }
            )
        }


    }
}