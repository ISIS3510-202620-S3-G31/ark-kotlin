package com.moviles.ark.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ColorScheme = lightColorScheme(
    primary = PrimaryColor,
    secondary = SecondaryColor,
    tertiary = AccentColor,
    background = BackgroundColor,
    surface = BackgroundColor,
    onPrimary = BackgroundColor,
    onSecondary = BackgroundColor,
    onBackground = TextColor,
    onSurface = TextColor,
    error = ErrorColor
)

//toda funcion que dibuja pantallas o aplica estilos debe marcarse con @Composable
//Unit es el equivalente a void
//MaterialTheme es un proveedor de estilos pa inyectarle mis colores y fuentes a todos sin ir uno por uno
@Composable
//le paso una funcion como parametro
fun Theme(content: @Composable () -> Unit): Unit {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography,
        content = content
    )
}