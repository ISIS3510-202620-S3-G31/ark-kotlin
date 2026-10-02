package com.moviles.ark.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.R
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.MoodCheckInViewModel
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onLogout: () -> Unit = {}
) {
    var showCheckInPopup by remember { mutableStateOf(false) }
    var isCheckInCompleted by remember { mutableStateOf(false) }
    var hasAutoPrompted by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> }

    LaunchedEffect(Unit) {
        //solicita permisos de localizacion al entrar
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        //espera un momento antes de mostrar el popup inicial automaticamente
        if (!hasAutoPrompted && !isCheckInCompleted) {
            delay(1500)
            hasAutoPrompted = true
            showCheckInPopup = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ark",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryColor
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.5f))
                        .clickable(onClick = onLogout)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Logout", style = MaterialTheme.typography.labelSmall, color = TextColor)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Image(
                painter = painterResource(R.drawable.ic_mascot_log),
                contentDescription = "Mascot",
                modifier = Modifier.size(110.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome back!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Track your wellbeing and explore tools",
                style = MaterialTheme.typography.bodyMedium,
                color = TextColor.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(28.dp))

            //si no ha completado el checkin muestra la tarjeta sutil para abrirlo
            if (!isCheckInCompleted) {
                MoodCheckInPromptCard(
                    onClick = { showCheckInPopup = true }
                )
            }
        }
    }

    if (showCheckInPopup) {
        Dialog(
            onDismissRequest = { showCheckInPopup = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .shadow(16.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.background)
            ) {
                MoodCheckInScreen(
                    viewModel = viewModel(factory = MoodCheckInViewModel.Factory),
                    onNavigateBack = { showCheckInPopup = false },
                    onCheckInSaved = {
                        isCheckInCompleted = true
                        showCheckInPopup = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MoodCheckInPromptCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2E3CD)),
        border = BorderStroke(1.dp, Color(0xFFE2CEB5))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryColor.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "Mood",
                        tint = TextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Daily Mood Check-in",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                    Text(
                        text = "How are you feeling right now? Tap to record",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextColor.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open check-in",
                tint = TextColor.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    AppTheme {
        HomeScreen()
    }
}
