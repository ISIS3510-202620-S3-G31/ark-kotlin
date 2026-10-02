package com.moviles.ark.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.moviles.ark.domain.repositories.AuthRepository
import com.moviles.ark.ui.screens.HomeScreen
import com.moviles.ark.ui.screens.LoginRoute
import com.moviles.ark.ui.screens.MoodCheckInScreen
import com.moviles.ark.ui.screens.RegisterScreen

@Composable
fun AppNavigation(
    authRepository: AuthRepository? = null
) {
    val navController = rememberNavController()
    //si ya hay una sesion activa en el celular entra directo a home
    val isUserLoggedIn = authRepository?.isLoggedIn() ?: false
    val startRoute = if (isUserLoggedIn) "home_screen" else "register_screen"

    NavHost(navController = navController, startDestination = startRoute) {
        composable("register_screen") {
            RegisterScreen(
                onNavigateToLogin = { navController.navigate("login_screen") },
                onRegisterSuccess = { navController.navigate("login_screen") }
            )
        }
        composable("login_screen") {
            LoginRoute(
                onNavigateToRegister = { navController.navigate("register_screen") },
                onLoginSuccess = {
                    navController.navigate("home_screen") {
                        popUpTo("login_screen") { inclusive = true }
                    }
                }
            )
        }
        composable("home_screen") {
            HomeScreen(
                onLogout = {
                    authRepository?.logout()
                    navController.navigate("login_screen") {
                        popUpTo(0)
                    }
                }
            )
        }
        composable("checkin_screen") {
            MoodCheckInScreen(
                onNavigateBack = { navController.popBackStack() },
                onCheckInSaved = { navController.popBackStack() }
            )
        }
    }
}