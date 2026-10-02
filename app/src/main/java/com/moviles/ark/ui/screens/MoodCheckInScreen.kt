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
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.ui.components.MoodSelectorComponent
import com.moviles.ark.ui.theme.AppTheme
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.82f))
                    .border(1.dp, Color(0xFFEADBCE), RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                    ) {
                        if (uiState.value.note.isEmpty()) {
                            Text(
                                text = "Add a note about your day (optional)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextColor.copy(alpha = 0.45f)
                            )
                        }
                        BasicTextField(
                            value = uiState.value.note,
                            onValueChange = { viewModel.onNoteChange(it) },
                            textStyle = TextStyle(
                                color = TextColor,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(PrimaryColor),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "${uiState.value.note.length} / 280",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextColor.copy(alpha = 0.45f),
                            fontSize = 10.5.sp
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
                val locationText = uiState.value.cityName ?: if (uiState.value.latitude != null) "Location captured" else "Detecting location..."
                Text(
                    text = locationText,
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