package com.moviles.ark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.domain.models.CheckInModel
import com.moviles.ark.ui.components.MoodSelectorComponent
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.MoodCheckInViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MoodCheckInScreen(
    viewModel: MoodCheckInViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
    onCheckInSaved: () -> Unit = {}
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val todayFormatted = remember {
        val formatter = SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH)
        formatter.format(Date())
    }

    LaunchedEffect(Unit) {
        viewModel.loadCurrentLocation()
    }

    if (uiState.value.showLocationDisabledDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLocationWarningDialog() },
            icon = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PrimaryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = "Location off",
                        tint = PrimaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Location Unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextColor
                )
            },
            text = {
                Text(
                    text = "Your GPS is turned off or permissions are disabled. Don't worry! Your check-in will be saved safely without regional data.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FigtreeFontFamily,
                    color = TextColor.copy(alpha = 0.75f),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissLocationWarningDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Got it",
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, end = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.55f))
                        .clickable(onClick = onNavigateBack),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextColor.copy(alpha = 0.75f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Mood Check-In",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.45f))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Today • $todayFormatted",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryColor,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(26.dp))
            MoodSelectorComponent(
                selectedEmotions = uiState.value.selectedEmotions,
                activeEmotion = uiState.value.activeEmotion,
                intensity = uiState.value.intensity,
                onEmotionToggle = { viewModel.onEmotionToggle(it) },
                onIntensityChange = { viewModel.onIntensityChange(it) }
            )
            Spacer(modifier = Modifier.height(22.dp))
            //caja de nota con paleta calida, sombra difusa y borde adaptativo de 20dp
            //calcula los caracteres restantes con coerceAtLeast para evitar negativos si ingresan emojis o caracteres multibyte
            val remainingChars = (CheckInModel.MAX_NOTE_LENGTH - uiState.value.note.length).coerceAtLeast(0)
            val isNearLimit = remainingChars <= 40
            val isAtLimit = remainingChars == 0
            //retroalimentacion visual progresiva: naranja primario cuando faltan 40 caracteres y rojo suave al agotarse
            val counterColor = when {
                isAtLimit -> Color(0xFFD32F2F)
                isNearLimit -> PrimaryColor
                else -> TextColor.copy(alpha = 0.50f)
            }
            //borde con acento de color dinamico para guiar visualmente al usuario antes de bloquear el tipeo
            val noteBorderColor = when {
                isAtLimit -> Color(0xFFD32F2F).copy(alpha = 0.7f)
                isNearLimit -> PrimaryColor.copy(alpha = 0.6f)
                else -> Color(0xFFEADBCE)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x12000000))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.90f))
                    .border(1.2.dp, noteBorderColor, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                    ) {
                        if (uiState.value.note.isEmpty()) {
                            Text(
                                text = "Add a note about your day (optional)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextColor.copy(alpha = 0.45f)
                            )
                        }
                        //filtro en composable para descartar eventos del teclado virtual cuando se alcanza el tope del modelo
                        BasicTextField(
                            value = uiState.value.note,
                            onValueChange = { input ->
                                if (input.length <= CheckInModel.MAX_NOTE_LENGTH) {
                                    viewModel.onNoteChange(input)
                                }
                            },
                            textStyle = TextStyle(
                                color = TextColor,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(PrimaryColor),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    //contador regresivo reactivo que indica al usuario con precision su margen disponible
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isAtLimit) "0 remaining (limit reached)" else "$remainingChars remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = counterColor,
                            fontWeight = if (isNearLimit) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text("📍", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                //"Location Disabled" sin permiso o con gps apagado; el check-in se guarda igual sin coordenadas (#144)
                Text(
                    text = uiState.value.locationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextColor.copy(alpha = 0.75f),
                    fontSize = 11.5.sp
                )
            }
            if (uiState.value.errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uiState.value.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    viewModel.saveCheckIn(onSavedCallback = onCheckInSaved)
                },
                enabled = !uiState.value.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (uiState.value.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save Check-In", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Mood Check-In Screen Preview")
@Composable
fun MoodCheckInScreenPreview() {
    AppTheme({
        MoodCheckInScreen()
    })
}