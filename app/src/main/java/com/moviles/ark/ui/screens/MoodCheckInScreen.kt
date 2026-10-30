package com.moviles.ark.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.domain.models.MoodCheckInModel
import com.moviles.ark.ui.components.CheckInSummaryComponent
import com.moviles.ark.ui.components.MoodSelectorComponent
import com.moviles.ark.ui.components.ModernVoiceInputBox
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.ScreenTopPadding
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.BackgroundColor
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

    //si ya se registro el check-in hoy, se presenta la vista de revision de solo lectura
    if (uiState.value.isAlreadyCheckedInToday) {
        MoodCheckInReviewContent(
            checkIn = uiState.value.existingCheckIn,
            onClose = onNavigateBack
        )
        return
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
                        color = BackgroundColor
                    )
                }
            },
            containerColor = BackgroundColor,
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
                //con el teclado abierto el area visible se achica y el scroll alcanza la nota y el boton de guardar
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
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
                        .background(TextColor.copy(alpha = 0.08f))
                        .clickable(onClick = onNavigateBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextColor.copy(alpha = 0.75f),
                        modifier = Modifier.size(16.dp)
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
                    .background(TextColor.copy(alpha = 0.06f))
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
            //caja de entrada de texto moderna con limite de caracteres y dictado por voz integrado
            ModernVoiceInputBox(
                value = uiState.value.note,
                onValueChange = { input -> viewModel.onNoteChange(input) },
                maxLength = MoodCheckInModel.MAX_NOTE_LENGTH,
                placeholder = "Add a note about your day (optional)..."
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(TextColor.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Location",
                    tint = PrimaryColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                //"Location Disabled" sin permiso o con gps apagado; el check-in se guarda igual sin coordenadas
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

//vista no editable de revision cuando el usuario ya realizo su check-in diario
//el boton Close de abajo la cierra; el resumen es el mismo del dialogo del home
@Composable
private fun MoodCheckInReviewContent(
    checkIn: MoodCheckInModel?,
    onClose: () -> Unit
) {
    val dateFormatted = remember(checkIn?.timestamp) {
        val date = if (checkIn != null) Date(checkIn.timestamp) else Date()
        val formatter = SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH)
        formatter.format(date)
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
                .padding(start = 22.dp, end = 22.dp, top = ScreenTopPadding, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = TextColor.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, TextColor.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = TextColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Checked-in Today",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextColor.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Mood Check-In",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = dateFormatted,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FigtreeFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryColor
            )

            Spacer(modifier = Modifier.height(22.dp))

            CheckInSummaryComponent(checkIn = checkIn)

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Close",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = BackgroundColor
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Mood Check-In Screen Preview")
@Composable
fun MoodCheckInScreenPreview() {
    AppTheme {
        MoodCheckInScreen()
    }
}
