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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.models.ToolCategory
import com.moviles.ark.R
import com.moviles.ark.data.repositories.FakeToolRepository
import com.moviles.ark.domain.models.Tool
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.MoodCheckInViewModel
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onNavigateToTool: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showCheckInPopup by remember { mutableStateOf(false) }
    var isCheckInCompleted by remember { mutableStateOf(false) }
    var hasAutoPrompted by remember { mutableStateOf(false) }

    var selectedCategory by remember { mutableStateOf("all") }
    var surpriseTool by remember { mutableStateOf<Tool?>(null) }

    val allTools = remember { FakeToolRepository.sampleTools }
    val categories = listOf("all", "calm_down", "release", "reflect", "celebrate")
    val categoryLabels = mapOf(
        "all" to "All",
        "calm_down" to "Calm down",
        "release" to "Release",
        "reflect" to "Reflect",
        "celebrate" to "Celebrate"
    )

    val filteredTools = if (selectedCategory == "all") {
        allTools
    } else {
        //la categoria ahora es el enum ToolCategory (#22); fromId traduce el id del filtro ("calm_down", etc.)
        allTools.filter { it.category == ToolCategory.fromId(selectedCategory) }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        //revisa si el usuario ya hizo su check-in en el dia de hoy
        val app = context.applicationContext as? ArkApplication
        val moodRepository = app?.container?.moodRepository
        val alreadyCheckedInToday = moodRepository?.hasCheckedInToday()?.getOrDefault(false) ?: false

        if (alreadyCheckedInToday) {
            isCheckInCompleted = true
        } else if (!hasAutoPrompted) {
            delay(1500)
            hasAutoPrompted = true
            showCheckInPopup = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundColor
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            //encabezado de la app
            item {
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
                }
            }

            //seccion de bienvenida con la mascota
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_mascot_log),
                        contentDescription = "Mascot",
                        modifier = Modifier.size(96.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Welcome back!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                    Text(
                        text = "Track your wellbeing and explore tools",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextColor.copy(alpha = 0.7f)
                    )
                }
            }

            //tarjeta para invitar a hacer el checkin si aun no lo ha hecho
            if (!isCheckInCompleted) {
                item {
                    MoodCheckInPromptCard(
                        onClick = { showCheckInPopup = true }
                    )
                }
            }

            //tarjeta leave it to chance
            item {
                LeaveItToChanceCard(
                    onSurpriseMe = {
                        surpriseTool = allTools.random()
                    }
                )
            }

            //banner si eligio una herramienta al azar
            if (surpriseTool != null) {
                item {
                    SurpriseResultBanner(
                        tool = surpriseTool!!,
                        onOpenTool = { onNavigateToTool(surpriseTool!!.id) }
                    )
                }
            }

            //pestañas de filtro por categoria
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(categories) { category ->
                        CategoryChip(
                            label = categoryLabels[category] ?: category,
                            isSelected = selectedCategory == category,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
            }

            //listado de herramientas del catalogo
            items(filteredTools) { tool ->
                ToolCardItem(
                    tool = tool,
                    onClick = { onNavigateToTool(tool.id) }
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

@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = if (isSelected) PrimaryColor else Color(0xFFEFE0C2),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = TextColor,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 4.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = TextColor,
            )
        }
    }
}

@Composable
private fun LeaveItToChanceCard(
    onSurpriseMe: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF221A15)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Leave it to chance",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFF0D0),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "✨",
                        fontSize = 18.sp,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Not sure what you need right now?\nWe'll pick one tool for you.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFD3C2A9),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onSurpriseMe,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = TextColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Surprise me",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextColor,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Image(
                painter = painterResource(id = R.drawable.ic_mascot_log),
                contentDescription = "Mascot",
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun SurpriseResultBanner(
    tool: Tool,
    onOpenTool: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTool() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SecondaryColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🎲 We picked for you:",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextColor.copy(alpha = 0.8f),
                )
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextColor,
                )
            }
            Button(
                onClick = onOpenTool,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Open", color = TextColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ToolCardItem(
    tool: Tool,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        color = Color(0xFFEFE0C2),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SecondaryColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = getToolIcon(tool.id),
                    contentDescription = tool.name,
                    tint = TextColor,
                    modifier = Modifier.size(26.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextColor,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextColor.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = TextColor.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun getToolIcon(toolId: String): ImageVector {
    return when (toolId) {
        "blow_it_out" -> Icons.Default.Whatshot
        "photo_of_the_day" -> Icons.Default.CameraAlt
        "custom_breathing" -> Icons.Default.Air
        "achievement_jar" -> Icons.Default.EmojiEvents
        "scream_tank" -> Icons.Default.Mic
        "body_mapping" -> Icons.Default.AccessibilityNew
        "emotion_detective" -> Icons.Default.Psychology
        else -> Icons.Default.AutoAwesome
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    AppTheme {
        HomeScreen()
    }
}
