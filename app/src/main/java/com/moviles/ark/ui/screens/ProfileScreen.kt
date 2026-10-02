package com.moviles.ark.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.ProfileUiState
import com.moviles.ark.ui.viewmodels.ProfileViewModel

//solo se usan colores de la paleta (ms6); las tarjetas son el texto muy transparente sobre el fondo
private val CardFill = TextColor.copy(alpha = 0.06f)

//pantalla sin estado: solo dibuja lo que le llega en uiState y avisa los clics
@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onSignOutClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Your profile", style = MaterialTheme.typography.headlineLarge, color = TextColor)
            Spacer(modifier = Modifier.height(32.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator(color = SecondaryColor)
            } else if (uiState.errorMessage != null) {
                Text(uiState.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetryClick) { Text("Try again", style = MaterialTheme.typography.labelLarge, color = TextColor) }
            } else {
                //avatar con iniciales, mismo circulo secondary del boton de perfil del home
                Box(
                    modifier = Modifier.size(88.dp).background(SecondaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.initials, style = MaterialTheme.typography.headlineMedium, color = TextColor)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(uiState.name, style = MaterialTheme.typography.headlineMedium, color = TextColor, textAlign = TextAlign.Center)
                Text(uiState.email, style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.7f))
                if (uiState.memberSince.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Member since ${uiState.memberSince}", style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.7f))
                }
                Spacer(modifier = Modifier.height(28.dp))

                //dos datos lado a lado: racha y dias usando la app
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        icon = Icons.Filled.Whatshot,
                        value = uiState.streakDays,
                        label = "day streak",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    StatCard(
                        icon = Icons.Filled.CalendarMonth,
                        value = uiState.daysWithArk,
                        label = if (uiState.daysWithArk == 1) "day with Ark" else "days with Ark",
                        modifier = Modifier.weight(1f)
                    )
                }

                //sin racha, en vez de solo mostrar un 0 se le dice al usuario como empezarla
                if (uiState.streakDays == 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Do a mood check-in today to start your streak.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            //cerrar sesion es una accion secundaria: borde secondary, texto en color de texto para que se lea bien
            OutlinedButton(
                onClick = onSignOutClick,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                border = BorderStroke(1.dp, SecondaryColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign out", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

//tarjeta de un dato: icono en cuadro secondary (como las tarjetas del home), numero y texto
@Composable
private fun StatCard(icon: ImageVector, value: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(CardFill, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(SecondaryColor, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TextColor, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("$value", style = MaterialTheme.typography.headlineMedium, color = TextColor)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.7f))
    }
}

//conecta la pantalla con el viewmodel
@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
    onSignedOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    //el viewmodel no navega: solo marca isSignedOut y la pantalla reacciona
    LaunchedEffect(uiState.isSignedOut) {
        if (uiState.isSignedOut) onSignedOut()
    }

    ProfileScreen(
        uiState = uiState,
        onSignOutClick = viewModel::onSignOutClick,
        onRetryClick = viewModel::loadProfile
    )
}

@Preview(showBackground = true, name = "Profile with streak")
@Composable
fun ProfileScreenPreview() {
    AppTheme {
        ProfileScreen(
            uiState = ProfileUiState(
                name = "Laura Avelino",
                initials = "LA",
                email = "laura@uniandes.edu.co",
                memberSince = "October 2026",
                daysWithArk = 12,
                streakDays = 3
            ),
            onSignOutClick = {},
            onRetryClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Profile without streak")
@Composable
fun ProfileScreenNoStreakPreview() {
    AppTheme {
        ProfileScreen(
            uiState = ProfileUiState(
                name = "lau3",
                initials = "L",
                email = "lau3@mail.com",
                memberSince = "October 2026",
                daysWithArk = 1
            ),
            onSignOutClick = {},
            onRetryClick = {}
        )
    }
}
