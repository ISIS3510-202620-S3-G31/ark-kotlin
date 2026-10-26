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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.R
import com.moviles.ark.data.local.AvatarPreferences
import com.moviles.ark.data.repositories.ToolRepositoryImpl
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import com.moviles.ark.domain.models.Tool
import com.moviles.ark.domain.models.ToolCategory
import com.moviles.ark.domain.models.WeatherCondition
import com.moviles.ark.domain.models.WeatherSuggestion
import com.moviles.ark.ui.components.RecommendationCardComponent
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.SoreanFontFamily
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.HomeUiState
import com.moviles.ark.ui.viewmodels.HomeViewModel
import com.moviles.ark.ui.viewmodels.MoodCheckInViewModel
import kotlinx.coroutines.delay

@Composable
fun HomeRoute(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    onNavigateToTool: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    //al volver de una herramienta se recalculan las recomendaciones con la nueva interaccion (#96)
    LaunchedEffect(Unit) {
        viewModel.onHomeShown()
    }

    LaunchedEffect(uiState.navigateToToolId) {
        uiState.navigateToToolId?.let { toolId ->
            onNavigateToTool(toolId)
            viewModel.onNavigatedToTool()
        }
    }

    HomeScreen(
        uiState = uiState,
        onCategorySelect = viewModel::onCategorySelect,
        onSurpriseMe = viewModel::onSurpriseMe,
        onToolClick = viewModel::onToolClick,
        onCheckInSaved = viewModel::onCheckInSaved,
        onNavigateToTool = onNavigateToTool
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState = HomeUiState(tools = ToolRepositoryImpl.sampleTools, filteredTools = ToolRepositoryImpl.sampleTools),
    onCategorySelect: (ToolCategory?) -> Unit = {},
    onSurpriseMe: () -> Unit = {},
    onToolClick: (String) -> Unit = {},
    onCheckInSaved: () -> Unit = {},
    onNavigateToTool: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showCheckInPopup by remember { mutableStateOf(false) }
    var hasAutoPrompted by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> }

    LaunchedEffect(uiState.isCheckInCompleted) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        if (!uiState.isCheckInCompleted && !hasAutoPrompted) {
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
            //encabezado de la app con boton discreto de Daily Check-in a un lado
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, PrimaryColor, CircleShape)
                                .background(SecondaryColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(AvatarPreferences.getDrawableRes(uiState.avatarId)),
                                contentDescription = "User Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(
                            text = "Ark",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryColor
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showCheckInPopup = true },
                        color = if (!uiState.isCheckInCompleted) PrimaryColor.copy(alpha = 0.15f) else Color(0xFFEFE0C2),
                        border = BorderStroke(1.dp, if (!uiState.isCheckInCompleted) PrimaryColor.copy(alpha = 0.4f) else Color.Transparent),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FavoriteBorder,
                                contentDescription = "Daily Check-in",
                                tint = if (!uiState.isCheckInCompleted) PrimaryColor else TextColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (!uiState.isCheckInCompleted) "Daily Check-in" else "Checked-in",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FigtreeFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (!uiState.isCheckInCompleted) PrimaryColor else TextColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            //seccion de bienvenida con la mascota dejando espacio comodo
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Image(
                            painter = painterResource(R.drawable.ic_mascot_log),
                            contentDescription = "Mascot",
                            modifier = Modifier.size(88.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .border(2.dp, BackgroundColor, CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(AvatarPreferences.getDrawableRes(uiState.avatarId)),
                                contentDescription = "Active Avatar Badge",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Welcome back!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = SoreanFontFamily,
                        color = TextColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Track your wellbeing and explore tools",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextColor.copy(alpha = 0.7f)
                    )
                }
            }

            //tarjeta interactiva para el check-in diario si aun no se ha completado hoy
            if (!uiState.isCheckInCompleted) {
                item {
                    DailyCheckInCard(
                        onClick = { showCheckInPopup = true }
                    )
                }
            }

            //encabezado de la seccion de recomendaciones
            item {
                Text(
                    text = "Today's Recommendations",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextColor,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            //1. recomendacion inteligente por Strategy (#16)
            val recommendedTool = uiState.tools.firstOrNull()
            if (recommendedTool != null) {
                item {
                    RecommendationCardComponent(
                        tool = recommendedTool,
                        rationaleTag = "Suggested for today",
                        onOpenTool = { toolId -> onToolClick(toolId) }
                    )
                }
            }

            //2. recomendacion al azar (Surprise me)
            item {
                LeaveItToChanceCard(
                    onSurpriseMe = onSurpriseMe
                )
            }

            //banner si eligio una herramienta al azar
            if (uiState.surpriseTool != null) {
                item {
                    SurpriseResultBanner(
                        tool = uiState.surpriseTool,
                        onOpenTool = { onToolClick(uiState.surpriseTool.id) }
                    )
                }
            }

            //3. recomendacion basada en el clima (#134): con sugerencia la tarjeta muestra la herramienta y el motivo
            //y Explore la abre; sin internet, sin ubicacion o con clima neutro queda la tarjeta general
            item {
                WeatherRecommendationCard(
                    suggestion = uiState.weatherSuggestion,
                    onClick = { onNavigateToTool("tools_catalog_screen") },
                    onOpenTool = { toolId -> onToolClick(toolId) }
                )
            }

            //acceso directo al catalogo completo de herramientas de regulacion
            item {
                BrowseCatalogBanner(
                    onOpenCatalog = { onNavigateToTool("tools_catalog_screen") }
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
                        showCheckInPopup = false
                        onCheckInSaved()
                    }
                )
            }
        }
    }
}

@Composable
private fun DailyCheckInCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryColor.copy(alpha = 0.15f)),
        border = BorderStroke(1.2.dp, PrimaryColor.copy(alpha = 0.45f))
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
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PrimaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "Daily Check-in",
                        tint = TextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "DAILY REFLECTION",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryColor,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "How are you feeling?",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextColor,
                        fontSize = 16.sp
                    )

                    Text(
                        text = "Log your check-in to personalize your practices.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FigtreeFontFamily,
                        color = TextColor.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(PrimaryColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Start check-in",
                    tint = TextColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun WeatherRecommendationCard(
    suggestion: WeatherSuggestion?,
    onClick: () -> Unit,
    onOpenTool: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    //con sugerencia del clima la tarjeta abre esa herramienta; sin ella sigue llevando al catalogo
    val onCardClick = { if (suggestion != null) onOpenTool(suggestion.tool.id) else onClick() }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onCardClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E4CF)),
        border = BorderStroke(1.2.dp, AccentColor.copy(alpha = 0.35f))
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
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AccentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (suggestion?.condition) {
                            WeatherCondition.RAINY -> Icons.Default.Umbrella
                            WeatherCondition.COLD -> Icons.Default.AcUnit
                            else -> Icons.Default.WbSunny
                        },
                        contentDescription = "Weather suggestion",
                        tint = AccentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "WEATHER SUGGESTION",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FigtreeFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = AccentColor,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                        if (suggestion == null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "SOON",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FigtreeFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = AccentColor,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = suggestion?.tool?.name ?: "Weather-based Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextColor,
                        fontSize = 16.sp
                    )

                    Text(
                        text = suggestion?.reason ?: "Tune your practice to today's climate and sunlight.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FigtreeFontFamily,
                        color = TextColor.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onCardClick() },
                color = AccentColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Explore",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = AccentColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun BrowseCatalogBanner(
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onOpenCatalog() },
        color = Color(0xFFEFE0C2),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, TextColor.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = null,
                    tint = SecondaryColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Browse Full Regulation Tools Catalog",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextColor,
                    fontSize = 13.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open catalog",
                tint = SecondaryColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun LeaveItToChanceCard(
    modifier: Modifier = Modifier,
    onSurpriseMe: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onSurpriseMe() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E4CF)),
        border = BorderStroke(1.2.dp, SecondaryColor.copy(alpha = 0.45f))
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
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SecondaryColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Casino,
                        contentDescription = null,
                        tint = SecondaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "SURPRISE TOOL",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryColor,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Leave it to chance",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextColor,
                        fontSize = 16.sp
                    )

                    Text(
                        text = "Not sure? Let Ark pick one tool for you.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FigtreeFontFamily,
                        color = TextColor.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SecondaryColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Surprise me",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
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
                    text = "WE PICKED FOR YOU",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextColor.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
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

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    AppTheme {
        HomeScreen()
    }
}
