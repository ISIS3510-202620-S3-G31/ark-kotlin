package com.moviles.ark.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.lerp

//todos los colores de material salen de la paleta, si no material pone sus blancos y grises por defecto
//(tarjetas, dialogos, hojas de abajo, menus, campos de texto y snackbars)
//las superficies van con el fondo un poco mas tostado y no con el fondo claro, asi nada se ve blanco
private val ColorScheme = lightColorScheme(
    primary = PrimaryColor,
    onPrimary = BackgroundColor,
    primaryContainer = lerp(BackgroundLightColor, PrimaryColor, 0.35f),
    onPrimaryContainer = TextColor,
    inversePrimary = PrimaryColor,
    secondary = SecondaryColor,
    onSecondary = BackgroundColor,
    secondaryContainer = lerp(BackgroundLightColor, SecondaryColor, 0.35f),
    onSecondaryContainer = TextColor,
    tertiary = AccentColor,
    onTertiary = BackgroundColor,
    tertiaryContainer = lerp(BackgroundLightColor, AccentColor, 0.30f),
    onTertiaryContainer = TextColor,
    background = BackgroundColor,
    onBackground = TextColor,
    surface = BackgroundColor,
    onSurface = TextColor,
    surfaceVariant = lerp(BackgroundColor, PrimaryColor, 0.10f),
    onSurfaceVariant = TextColor,
    surfaceTint = PrimaryColor,
    inverseSurface = TextColor,
    inverseOnSurface = BackgroundColor,
    error = ErrorColor,
    onError = BackgroundColor,
    errorContainer = lerp(BackgroundLightColor, ErrorColor, 0.25f),
    onErrorContainer = TextColor,
    outline = lerp(BackgroundColor, TextColor, 0.45f),
    outlineVariant = lerp(BackgroundColor, TextColor, 0.2f),
    surfaceBright = BackgroundColor,
    surfaceDim = lerp(BackgroundColor, PrimaryColor, 0.15f),
    surfaceContainerLowest = BackgroundColor,
    surfaceContainerLow = lerp(BackgroundColor, PrimaryColor, 0.05f),
    surfaceContainer = lerp(BackgroundColor, PrimaryColor, 0.08f),
    surfaceContainerHigh = lerp(BackgroundColor, PrimaryColor, 0.11f),
    surfaceContainerHighest = lerp(BackgroundColor, PrimaryColor, 0.14f)
)

//toda funcion que dibuja pantallas o aplica estilos debe marcarse con @Composable
//Unit es el equivalente a void
//MaterialTheme es un proveedor de estilos pa inyectarle mis colores y fuentes a todos sin ir uno por uno
@Composable
//le paso una funcion como parametro
fun AppTheme(content: @Composable () -> Unit): Unit {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography,
        content = content
    )
}
