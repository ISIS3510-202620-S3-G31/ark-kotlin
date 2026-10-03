package com.moviles.ark.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.repositories.AuthRepository
import com.moviles.ark.ui.components.BottomNavItem
import com.moviles.ark.ui.components.BottomNavigationBar
import com.moviles.ark.ui.components.OfflineBannerComponent
import com.moviles.ark.ui.screens.CustomBreathingRoute
import com.moviles.ark.ui.screens.HomeScreen
import com.moviles.ark.ui.screens.LoginRoute
import com.moviles.ark.ui.screens.MoodCheckInScreen
import com.moviles.ark.ui.screens.PhotoOfTheDayRoute
import com.moviles.ark.ui.screens.PlaceholderScreen
import com.moviles.ark.ui.screens.ProfileRoute
import com.moviles.ark.ui.screens.RegisterScreen
import com.moviles.ark.ui.screens.StatsScreen
import com.moviles.ark.ui.theme.BackgroundColor

@Composable
fun AppNavigation(
    authRepository: AuthRepository? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as? ArkApplication
    val connectivityObserver = app?.container?.networkConnectivityObserver

    //observa en tiempo real el estado de la conexion para el banner offline (#32)
    val isConnected by (connectivityObserver?.observe() ?: kotlinx.coroutines.flow.flowOf(true))
        .collectAsStateWithLifecycle(initialValue = connectivityObserver?.isConnected() ?: true)

    //si ya hay una sesion activa en el celular entra directo a home
    val isUserLoggedIn = authRepository?.isLoggedIn() ?: false
    val startRoute = if (isUserLoggedIn) "home_screen" else "register_screen"

    //ruta que se esta mostrando, para saber si va la barra y que pestana resaltar
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    //contentWindowInsets en 0: cada pantalla ya maneja su barra de estado (statusBarsPadding),
    //el scaffold solo deja el espacio de abajo para la barra de navegacion
    Scaffold(
        containerColor = BackgroundColor,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            //banner no invasivo de conexion offline (#32)
            OfflineBannerComponent(isConnected = isConnected)
        },
        bottomBar = {
            if (currentRoute in BottomNavItem.routes) {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onItemClick = { item ->
                        navController.navigate(item.route) {
                            //home es la base de las pestanas: "atras" desde stats o profile vuelve a home
                            popUpTo("home_screen") { saveState = true }
                            //tocar la pestana actual no abre otra copia de la pantalla
                            launchSingleTop = true
                            //al volver a una pestana se recupera como estaba
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
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
                        //se borra todo el historial: con sesion iniciada "atras" no debe volver a registro ni login
                        navController.navigate("home_screen") {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                )
            }
            composable("home_screen") {
                com.moviles.ark.ui.screens.HomeRoute(
                    onNavigateToTool = { toolId ->
                        when (toolId) {
                            "photo_of_the_day" -> navController.navigate("photo_of_the_day_screen")
                            "custom_breathing" -> navController.navigate("custom_breathing_screen")
                        }
                    }
                )
            }
            composable("stats_screen") {
                StatsScreen(
                    onNavigateToHome = {
                        navController.navigate("home_screen") {
                            popUpTo("home_screen") { saveState = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            //pestana profile: al cerrar sesion vuelve al login y borra el historial
            composable("profile_screen") {
                ProfileRoute(onSignedOut = {
                    navController.navigate("login_screen") {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                })
            }
            //herramienta foto del dia (#25); se abre desde el catalogo de herramientas
            composable("photo_of_the_day_screen") {
                PhotoOfTheDayRoute(onBack = { navController.popBackStack() })
            }
            //herramienta de respiracion personalizada (#19)
            composable("custom_breathing_screen") {
                CustomBreathingRoute(onBack = { navController.popBackStack() })
            }
            composable("checkin_screen") {
                MoodCheckInScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onCheckInSaved = { navController.popBackStack() }
                )
            }
        }
    }
}
