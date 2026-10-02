package com.moviles.ark.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.moviles.ark.ui.screens.LoginRoute
import com.moviles.ark.ui.screens.RegisterScreen

@Composable
fun AppNavigation() {
    //el controlador que gestiona el historial de pantallas
    val navController = rememberNavController()

    //el contenedor que define que pantalla mostrar segun la ruta
    NavHost(navController = navController, startDestination = "register_screen") {
        //ruta de login
        composable("login_screen") {
            LoginRoute(onNavigateToRegister = {
                navController.navigate("register_screen")
            })
        }

        //ruta de registro
        composable("register_screen") {
            RegisterScreen(
                //se deja entre llaves para guardarla y ejecutarla cuando toque log in
                onNavigateToLogin = {navController.navigate("login_screen") },
                onRegisterSuccess = {navController.navigate("login_screen") }
            )
        }
    }
}