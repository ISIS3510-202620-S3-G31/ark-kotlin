package com.moviles.ark.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor

//pestanas de la barra, en el orden en que se ven: stats | home | profile
//route es la ruta del NavHost que abre cada una
enum class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    STATS("stats_screen", "Stats", Icons.Filled.BarChart),
    HOME("home_screen", "Home", Icons.Filled.Home),
    PROFILE("profile_screen", "Profile", Icons.Filled.Person);

    companion object {
        //rutas donde se muestra la barra; en login, registro o el check-in no aparece
        val routes = entries.map { it.route }.toSet()
    }
}

//barra sin estado: recibe la ruta actual para resaltar la pestana y avisa cual se toco
//no conoce el NavController, la navegacion se hace en el NavHost
@Composable
fun BottomNavigationBar(
    currentRoute: String?,
    onItemClick: (BottomNavItem) -> Unit
) {
    Column {
        //linea fina para separar la barra del contenido, que tiene el mismo color de fondo
        HorizontalDivider(color = TextColor.copy(alpha = 0.12f))
        NavigationBar(containerColor = BackgroundColor, tonalElevation = 0.dp) {
            BottomNavItem.entries.forEach { item ->
                NavigationBarItem(
                    selected = currentRoute == item.route,
                    onClick = { onItemClick(item) },
                    //iconos de 22 dp en color de texto, como pide el manual de marca (ms6)
                    icon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    //se fija labelLarge (figtree) para no usar la letra por defecto de material
                    label = { Text(item.label, style = MaterialTheme.typography.labelLarge) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TextColor,
                        selectedTextColor = TextColor,
                        //la pestana activa se resalta con el color secondary, que la paleta reserva para navegacion
                        indicatorColor = SecondaryColor,
                        unselectedIconColor = TextColor.copy(alpha = 0.6f),
                        unselectedTextColor = TextColor.copy(alpha = 0.6f)
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Bottom bar on Home")
@Composable
fun BottomNavigationBarPreview() {
    AppTheme {
        BottomNavigationBar(currentRoute = BottomNavItem.HOME.route, onItemClick = {})
    }
}
