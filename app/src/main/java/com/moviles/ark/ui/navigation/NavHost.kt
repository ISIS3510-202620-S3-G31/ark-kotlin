package com.moviles.ark.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moviles.ark.domain.models.ToolCategory
import com.moviles.ark.ui.components.BottomNavItem
import com.moviles.ark.ui.components.BottomNavigationBar
import com.moviles.ark.ui.components.OfflineBannerComponent
import com.moviles.ark.ui.screens.CustomBreathingRoute
import com.moviles.ark.ui.screens.HomeRoute
import com.moviles.ark.ui.screens.LoginRoute
import com.moviles.ark.ui.screens.MoodCheckInScreen
import com.moviles.ark.ui.screens.PhotoOfTheDayRoute
import com.moviles.ark.ui.screens.PostToolFeedbackRoute
import com.moviles.ark.ui.screens.ProfileRoute
import com.moviles.ark.ui.screens.RegisterScreen
import com.moviles.ark.ui.screens.ScreamTankRoute
import com.moviles.ark.ui.screens.StatsScreen
import com.moviles.ark.ui.screens.ToolsCatalogRoute
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.viewmodels.AppViewModel

@Composable
fun AppNavigation(
    appViewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)
) {
    val navController = rememberNavController()
    //ruta inicial (con o sin sesion) y conexion para el banner offline: las decide el viewmodel
    val appState by appViewModel.uiState.collectAsStateWithLifecycle()
    val isConnected = appState.isConnected
    //la ruta inicial se toma una sola vez para que el NavHost no se reinicie
    val startRoute = remember { appState.startRoute }

    //ruta que se esta mostrando, para saber si va la barra y que pestana resaltar
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    //contentWindowInsets en 0: cada pantalla ya maneja su barra de estado (statusBarsPadding),
    //el scaffold solo deja el espacio de abajo para la barra de navegacion
    Scaffold(
        containerColor = BackgroundColor,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            //banner no invasivo de conexion offline
            OfflineBannerComponent(isConnected = isConnected)
        },
        bottomBar = {
            if (currentRoute in BottomNavItem.routes) {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onItemClick = { item -> navController.navigateToTab(item.route) }
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
                    onRegisterSuccess = {
                        //al registrarse en firebase la sesion queda iniciada automaticamente,
                        //por lo que se navega directo a home limpiando el historial para no volver a registro
                        navController.navigate("home_screen") {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
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
                HomeRoute(
                    onNavigateToTool = { toolId ->
                        when (toolId) {
                            "photo_of_the_day" -> navController.navigate("photo_of_the_day_screen")
                            "custom_breathing" -> navController.navigate("custom_breathing_screen")
                            "scream_tank" -> navController.navigate("scream_tank_screen")
                            //el catalogo es una pestana: se abre igual que desde la barra para no dejar el stack trabado
                            "tools_catalog_screen" -> navController.navigateToTab("tools_catalog_screen")
                        }
                    }
                )
            }
            composable("tools_catalog_screen") { backStackEntry ->
                //categoria que deja la tarjeta de insight de stats; se aplica una vez y vuelve a null
                val catalogViewModel: com.moviles.ark.ui.viewmodels.HomeViewModel =
                    viewModel(factory = com.moviles.ark.ui.viewmodels.HomeViewModel.Factory)
                val pendingCategoryId by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(TOOLS_CATEGORY_KEY, null)
                    .collectAsStateWithLifecycle()
                LaunchedEffect(pendingCategoryId) {
                    pendingCategoryId?.let { id ->
                        catalogViewModel.onCategorySelect(ToolCategory.fromId(id))
                        backStackEntry.savedStateHandle[TOOLS_CATEGORY_KEY] = null
                    }
                }
                ToolsCatalogRoute(
                    viewModel = catalogViewModel,
                    onNavigateToTool = { toolId ->
                        when (toolId) {
                            "photo_of_the_day" -> navController.navigate("photo_of_the_day_screen")
                            "custom_breathing" -> navController.navigate("custom_breathing_screen")
                            "scream_tank" -> navController.navigate("scream_tank_screen")
                        }
                    }
                )
            }
            composable("stats_screen") {
                StatsScreen(
                    //la tarjeta de insight abre el catalogo con la categoria ya filtrada
                    onNavigateToToolsCatalog = { category ->
                        navController.navigateToTab("tools_catalog_screen")
                        if (category != null) {
                            navController.currentBackStackEntry?.savedStateHandle?.set(TOOLS_CATEGORY_KEY, category.id)
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
            //herramienta foto del dia; al guardar la foto navega a feedback
            composable("photo_of_the_day_screen") {
                PhotoOfTheDayRoute(
                    //vuelve a donde se abrio: home o el catalogo
                    onBack = { navController.popBackStack() },
                    onPhotoSaved = { duration ->
                        navController.navigate("post_tool_feedback/photo_of_the_day?duration=$duration") {
                            popUpTo("photo_of_the_day_screen") { inclusive = true }
                        }
                    }
                )
            }
            //herramienta de respiracion guiada; si completo al menos 1 ciclo navega a feedback
            composable("custom_breathing_screen") {
                CustomBreathingRoute(onBack = { wasCompleted, duration ->
                    if (wasCompleted) {
                        navController.navigate("post_tool_feedback/custom_breathing?duration=$duration") {
                            popUpTo("custom_breathing_screen") { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                })
            }
            //herramienta scream tank; si el microfono alcanzo a escuchar navega a feedback
            composable("scream_tank_screen") {
                ScreamTankRoute(onBack = { wasCompleted, duration ->
                    if (wasCompleted) {
                        navController.navigate("post_tool_feedback/scream_tank?duration=$duration") {
                            popUpTo("scream_tank_screen") { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                })
            }
            composable("checkin_screen") {
                MoodCheckInScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onCheckInSaved = { navController.popBackStack() }
                )
            }
            //pantalla de retroalimentacion post-herramienta
            composable(
                route = "post_tool_feedback/{toolId}?duration={duration}",
                arguments = listOf(
                    navArgument("toolId") { type = NavType.StringType },
                    navArgument("duration") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { backStackEntry ->
                val toolId = backStackEntry.arguments?.getString("toolId") ?: ""
                val duration = backStackEntry.arguments?.getInt("duration") ?: 0
                PostToolFeedbackRoute(
                    toolId = toolId,
                    durationSeconds = duration,
                    onFeedbackSubmitted = {
                        navController.popBackStack("home_screen", inclusive = false)
                    },
                    onDismiss = {
                        navController.popBackStack("home_screen", inclusive = false)
                    }
                )
            }
        }
    }
}

//abre una pestana de la barra con home como base: "atras" desde cualquier pestana vuelve a home
//home se trata aparte: navegar a la misma ruta del popUpTo con restoreState volvia a montar la pestana
//que se acababa de guardar (por eso el boton Home dejaba al usuario en el catalogo)
private fun NavHostController.navigateToTab(route: String) {
    if (route == "home_screen") {
        if (currentDestination?.route == "home_screen") return
        //si home no esta en el historial (no deberia pasar con sesion iniciada) se abre de cero
        if (!popBackStack("home_screen", inclusive = false)) navigate("home_screen")
        return
    }
    navigate(route) {
        popUpTo("home_screen") { saveState = true }
        //tocar la pestana actual no abre otra copia de la pantalla
        launchSingleTop = true
        //al volver a una pestana se recupera como estaba
        restoreState = true
    }
}

//clave del savedStateHandle con la que stats le pasa al catalogo la categoria a filtrar
private const val TOOLS_CATEGORY_KEY = "tools_category"

