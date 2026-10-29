package com.moviles.ark.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.models.StatsCalculator
import com.moviles.ark.domain.models.EmotionShare
import com.moviles.ark.domain.models.StatsModel
import com.moviles.ark.domain.models.StatsVisitSummary
import com.moviles.ark.domain.models.ToolCategory
import com.moviles.ark.ui.components.getEmotionColor
import com.moviles.ark.ui.components.getEmotionEmoji
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.SoreanFontFamily
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.StatsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

//paleta consistente de superficies de tarjeta sobre BackgroundColor
private val CardSurfaceColor = Color(0xFFF3E4CF)
private val CardBorderColor = TextColor.copy(alpha = 0.12f)

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
    onNavigateToToolsCatalog: (ToolCategory?) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    //cada entrada a la pantalla cuenta una visita y recarga con cache-then-network; girar el telefono no cuenta
    LaunchedEffect(Unit) {
        viewModel.onScreenOpened()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.onScreenClosed((context as? Activity)?.isChangingConfigurations == true) }
    }

    //aviso corto cuando el refresco no pudo traer datos nuevos
    LaunchedEffect(uiState.noticeMessage) {
        uiState.noticeMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.onNoticeShown()
        }
    }

    StatsScreenContent(
        stats = uiState.stats,
        isLoading = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        isRefreshing = uiState.isRefreshing,
        isOffline = uiState.isOffline,
        visitSummary = uiState.visitSummary,
        onRefresh = { viewModel.refresh() },
        onNavigateToToolsCatalog = onNavigateToToolsCatalog
    )
}

//vista desacoplada sin viewmodel para permitir renderizado fluido en preview
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreenContent(
    stats: StatsModel?,
    isLoading: Boolean,
    errorMessage: String? = null,
    isRefreshing: Boolean = false,
    isOffline: Boolean = false,
    visitSummary: StatsVisitSummary = StatsVisitSummary(),
    onRefresh: () -> Unit = {},
    onNavigateToToolsCatalog: (ToolCategory?) -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            //barra superior con titulo principal centrado, ultima actualizacion y boton de refresco
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 12.dp)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Wellbeing Stats",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Last updated: ${getLastUpdatedLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextColor.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .align(Alignment.CenterEnd)
                        .shadow(4.dp, CircleShape, ambientColor = Color(0x35351B08), spotColor = Color(0x35351B08))
                        .clip(CircleShape)
                        .background(CardSurfaceColor)
                        .border(1.dp, CardBorderColor, CircleShape)
                        .clickable { onRefresh() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = TextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            //deslizar hacia abajo pide datos nuevos al backend en firestore
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                state = pullState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        color = PrimaryColor,
                        containerColor = BackgroundColor
                    )
                }
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryColor)
                    }
                } else if (stats == null) {
                    PullableContent {
                        if (isOffline) {
                            //cuando no hay nada en disco y no hay internet: fallback ilustrado amigable
                            StatsOfflineView(onRetry = onRefresh)
                        } else if (errorMessage != null) {
                            StatsErrorView(
                                message = errorMessage,
                                onRetry = onRefresh
                            )
                        } else {
                            StatsEmptyView(
                                onExploreTools = { onNavigateToToolsCatalog(null) }
                            )
                        }
                    }
                } else if (stats.isEmpty()) {
                    PullableContent {
                        StatsEmptyView(
                            onExploreTools = { onNavigateToToolsCatalog(null) }
                        )
                    }
                } else {
                    StatsMainView(stats = stats, visitSummary = visitSummary, onNavigateToToolsCatalog = onNavigateToToolsCatalog)
                }
            }
        }
    }
}

//las vistas fijas van en una lista de un solo item para que el gesto de pull-to-refresh tambien funcione en ellas
@Composable
private fun PullableContent(content: @Composable () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(modifier = Modifier.fillParentMaxSize()) {
                content()
            }
        }
    }
}

//vista cuando ocurre un error de servidor al cargar estadisticas
@Composable
private fun StatsErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x35351B08), spotColor = Color(0x35351B08)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
            border = BorderStroke(1.2.dp, CardBorderColor)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE57373).copy(alpha = 0.18f))
                        .border(1.2.dp, Color(0xFFE8D3B9), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Unable to load stats",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = SoreanFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FigtreeFontFamily,
                    color = TextColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryColor)
                        .clickable { onRetry() }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    color = PrimaryColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Retry",
                        fontFamily = FigtreeFontFamily,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

//tarjeta ilustrada cuando no hay datos en disco y no hay conexion a internet
@Composable
private fun StatsOfflineView(
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x35351B08), spotColor = Color(0x35351B08)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
            border = BorderStroke(1.2.dp, CardBorderColor)
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                //composicion ilustrada calida con icono de offline
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(PrimaryColor.copy(alpha = 0.18f))
                        .border(1.2.dp, Color(0xFFE8D3B9), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(SecondaryColor.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = "Offline indicator",
                            tint = SecondaryColor,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "You're Currently Offline",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = SoreanFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Ark needs an internet connection to fetch your latest cloud analytics for the first time. Check your Wi-Fi or data, and swipe down or tap retry to connect!",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FigtreeFontFamily,
                    color = TextColor.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                ) {
                    Text(
                        text = "Retry Connection",
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextColor,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

//tarjeta ilustrada de estado vacio cuando el usuario aun no ha registrado check-ins ni herramientas
@Composable
private fun StatsEmptyView(
    onExploreTools: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x35351B08), spotColor = Color(0x35351B08)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
            border = BorderStroke(1.2.dp, CardBorderColor)
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                //composicion ilustrada con halo concentrico calido de la paleta oficial
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(PrimaryColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PrimaryColor.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoGraph,
                            contentDescription = "Illustrated analytics",
                            tint = PrimaryColor,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Your Wellbeing Journey Awaits",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = SoreanFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Track your daily emotional check-ins and explore guided wellbeing tools to unlock personalized analytics and healthy habit insights.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FigtreeFontFamily,
                    color = TextColor.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onExploreTools,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                ) {
                    Text(
                        text = "Explore Wellbeing Tools",
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextColor,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

private fun getLastUpdatedLabel(): String {
    val format = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    return "Today at " + format.format(java.util.Date())
}

data class TooltipInfo(val title: String, val message: String)

@Composable
private fun StatsMainView(
    stats: StatsModel,
    visitSummary: StatsVisitSummary = StatsVisitSummary(),
    onNavigateToToolsCatalog: (ToolCategory?) -> Unit = {}
) {
    var activeTooltip by remember { mutableStateOf<TooltipInfo?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        //capsulas de resumen directo y limpio
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                //capsula 1 (izquierda): top feeling con relieve y paleta oficial
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(5.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x30351B08), spotColor = Color(0x30351B08)),
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurfaceColor,
                    border = BorderStroke(1.dp, CardBorderColor)
                ) {
                    val emotion = stats.topEmotion ?: Emotion.HAPPINESS
                    val color = getEmotionColor(emotion)
                    val emoji = getEmotionEmoji(emotion)
                    val emotionName = emotion.name.lowercase().replaceFirstChar { it.uppercase() }

                    Row(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFF7ECDC), Color(0xFFEADBCE))
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .drawBehind {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                color.copy(alpha = 0.75f),
                                                color.copy(alpha = 0.25f),
                                                Color.Transparent
                                            ),
                                            radius = 24.dp.toPx()
                                        ),
                                        radius = 24.dp.toPx()
                                    )
                                }
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.22f))
                                .border(1.2.dp, Color(0xFFE8D3B9), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 21.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Top Feeling",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextColor.copy(alpha = 0.65f),
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryColor.copy(alpha = 0.15f))
                                        .clickable {
                                            activeTooltip = TooltipInfo(
                                                "Top Feeling",
                                                "The emotion you registered most frequently across all your check-ins."
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Info",
                                        tint = PrimaryColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Text(
                                text = emotionName,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FigtreeFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = TextColor,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                //capsula 2 (derecha): total de checkins con profundidad e icono tactil
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(5.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x30351B08), spotColor = Color(0x30351B08)),
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurfaceColor,
                    border = BorderStroke(1.dp, CardBorderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFF7ECDC), Color(0xFFEADBCE))
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .drawBehind {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                PrimaryColor.copy(alpha = 0.45f),
                                                PrimaryColor.copy(alpha = 0.15f),
                                                Color.Transparent
                                            ),
                                            radius = 24.dp.toPx()
                                        ),
                                        radius = 24.dp.toPx()
                                    )
                                }
                                .clip(CircleShape)
                                .background(PrimaryColor.copy(alpha = 0.18f))
                                .border(1.2.dp, Color(0xFFE8D3B9), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = PrimaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Check-ins",
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FigtreeFontFamily,
                                color = TextColor.copy(alpha = 0.65f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${stats.totalCheckIns}",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FigtreeFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = TextColor,
                                fontSize = 17.sp
                            )
                        }
                    }
                }
            }
        }

        //cuantas veces entro a sus estadisticas esta semana y su promedio; sube en vivo con cada entrada
        if (visitSummary.hasVisits()) {
            item {
                StatsVisitsCard(
                    summary = visitSummary,
                    onShowTooltip = { activeTooltip = it }
                )
            }
        }

        //grafico de barras: journey semanal de emociones con relieve y escala 1 a 5
        item {
            WeeklyEmotionJourneyCard(
                weeklyIntensities = stats.weeklyIntensities,
                weeklyEmotions = stats.weeklyEmotions,
                onShowTooltip = { activeTooltip = it }
            )
        }

        //distribucion de frecuencias discretas de emociones (reemplaza promedios planos)
        item {
            EmotionDistributionCard(
                emotionFrequencies = stats.emotionFrequencies,
                distribution = stats.emotionDistribution(),
                totalRecords = stats.emotionRecordCount(),
                onShowTooltip = { activeTooltip = it }
            )
        }

        //grafico de torta: frecuencia de herramientas con relieve
        item {
            ToolsUsagePieChartCard(toolUsagePercentages = stats.toolUsagePercentages)
        }
    }

    if (activeTooltip != null) {
        val tooltip = activeTooltip!!
        AlertDialog(
            onDismissRequest = { activeTooltip = null },
            title = {
                Text(
                    text = tooltip.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextColor
                )
            },
            text = {
                Text(
                    text = tooltip.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextColor.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = { activeTooltip = null }) {
                    Text("OK", color = PrimaryColor, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = Color(0xFFFFF9EE),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

//tarjeta de visitas a estadisticas: esta semana a la izquierda y el promedio semanal a la derecha
@Composable
private fun StatsVisitsCard(
    summary: StatsVisitSummary,
    onShowTooltip: (TooltipInfo) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(5.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x30351B08), spotColor = Color(0x30351B08)),
        shape = RoundedCornerShape(20.dp),
        color = CardSurfaceColor,
        border = BorderStroke(1.dp, CardBorderColor)
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(colors = listOf(Color(0xFFF7ECDC), Color(0xFFEADBCE))))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Your stats visits",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextColor,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(PrimaryColor.copy(alpha = 0.15f))
                        .clickable {
                            onShowTooltip(
                                TooltipInfo(
                                    "Your stats visits",
                                    "How many times you opened your stats this week (Monday to today) and your weekly average since your first visit. It updates every time you come back."
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = PrimaryColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                VisitsNumber(
                    value = "${summary.thisWeek}",
                    label = "this week",
                    modifier = Modifier.weight(1f)
                )
                VisitsNumber(
                    value = String.format(Locale.US, "%.1f", summary.weeklyAverage),
                    label = if (summary.weeksTracked == 1) "per week (1 week)" else "per week (${summary.weeksTracked} weeks)",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VisitsNumber(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Bold,
            color = PrimaryColor,
            fontSize = 24.sp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FigtreeFontFamily,
            color = TextColor.copy(alpha = 0.65f),
            fontSize = 12.sp
        )
    }
}

//grafico de barras para el journey emocional de la semana con escala visible de 1 a 5 y tipografia oficial
@Composable
private fun WeeklyEmotionJourneyCard(
    weeklyIntensities: Map<String, Float>,
    weeklyEmotions: Map<String, Emotion>,
    onShowTooltip: (TooltipInfo) -> Unit = {}
) {
    //orden fijo de hace 6 dias hasta hoy, sin depender del orden del mapa que llega de firestore o del cache
    val dayLabels = StatsCalculator.lastSevenDayLabels()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x35351B08), spotColor = Color(0x35351B08)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
        border = BorderStroke(1.dp, CardBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Weekly Emotion Journey",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(PrimaryColor.copy(alpha = 0.15f))
                                .clickable {
                                    onShowTooltip(
                                        TooltipInfo(
                                            "Weekly Emotion Journey",
                                            "Shows the dominant emotion and its highest intensity recorded for each day of the past week."
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = PrimaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = "Dominant emotion per day and its intensity",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FigtreeFontFamily,
                        color = TextColor.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = TextColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            //area del grafico con eje Y (escala 1 a 5)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(165.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                //eje Y con numeros del 5 al 1
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    for (level in 5 downTo 1) {
                        Text(
                            text = "$level",
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FigtreeFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextColor.copy(alpha = 0.45f)
                        )
                    }
                }

                //columnas del grafico de barras para cada dia de la semana
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    for (day in dayLabels) {
                        val emotion = weeklyEmotions[day]
                        val intensity = weeklyIntensities[day] ?: 0f
                        val hasEntry = intensity > 0f && emotion != null
                        val barColor = if (emotion != null) getEmotionColor(emotion) else PrimaryColor
                        val emoji = if (emotion != null) getEmotionEmoji(emotion) else ""
                        val intensityRatio = StatsModel.intensityRatio(intensity)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (hasEntry && emoji.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .drawBehind {
                                            drawCircle(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        barColor.copy(alpha = 0.75f),
                                                        barColor.copy(alpha = 0.25f),
                                                        Color.Transparent
                                                    ),
                                                    radius = 18.dp.toPx()
                                                ),
                                                radius = 18.dp.toPx()
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = emoji,
                                        fontSize = 20.sp
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.size(34.dp))
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            //barra de altura proporcional al nivel 1..5 con relieve
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .fillMaxHeight(fraction = if (hasEntry) intensityRatio.coerceAtLeast(0.12f) else 0.08f)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                    .background(
                                        if (hasEntry) {
                                            Brush.verticalGradient(
                                                colors = listOf(barColor, barColor.copy(alpha = 0.72f))
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                colors = listOf(Color(0xFFEADBCE), Color(0xFFDFCCBC))
                                            )
                                        }
                                    )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            //etiqueta del dia con tipografia oficial
                            Text(
                                text = day.take(3),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FigtreeFontFamily,
                                fontSize = 11.sp,
                                fontWeight = if (hasEntry) FontWeight.Bold else FontWeight.Normal,
                                color = if (hasEntry) TextColor else TextColor.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        }
    }
}


//tarjeta de distribucion de frecuencias de emociones discretas
@Composable
private fun EmotionDistributionCard(
    emotionFrequencies: Map<Emotion, Int>,
    distribution: List<EmotionShare>,
    totalRecords: Int,
    onShowTooltip: (TooltipInfo) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x35351B08), spotColor = Color(0x35351B08)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
        border = BorderStroke(1.dp, CardBorderColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Emotion Distribution",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(PrimaryColor.copy(alpha = 0.15f))
                            .clickable {
                                onShowTooltip(
                                    TooltipInfo(
                                        "Emotion Distribution",
                                        "Shows the frequency count of each emotion across all check-ins rather than a flat average."
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = PrimaryColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = if (emotionFrequencies.isEmpty()) "" else "$totalRecords check-ins",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextColor.copy(alpha = 0.65f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (emotionFrequencies.isEmpty()) {
                Text(
                    text = "No emotion records yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextColor.copy(alpha = 0.5f)
                )
            } else {
                //el modelo ordena las emociones y calcula los porcentajes
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (share in distribution) {
                        val (emotion, count, percentage) = share
                        val color = getEmotionColor(emotion)
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = emotion.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = TextColor,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "$count ($percentage%)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextColor.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    //pista de la barra con el color de texto suave, como los fondos vacios del resto de la app
                                    .background(TextColor.copy(alpha = 0.08f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = share.fraction)
                                        .fillMaxHeight()
                                        .background(color)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

//grafico de torta con distribucion porcentual de herramientas y relieve
@Composable
private fun ToolsUsagePieChartCard(
    toolUsagePercentages: Map<String, Float>
) {
    val sliceColors = listOf(
        PrimaryColor,
        SecondaryColor,
        AccentColor,
        Color(0xFF5DBB63),
        Color(0xFF8E44AD),
        Color(0xFFF39C12)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x35351B08), spotColor = Color(0x35351B08)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
        border = BorderStroke(1.dp, CardBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Tools Frequency",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = SoreanFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                    Text(
                        text = "Historical distribution by % of tool usage",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FigtreeFontFamily,
                        color = TextColor.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.PieChart,
                    contentDescription = null,
                    tint = TextColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (toolUsagePercentages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tool usage recorded yet",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FigtreeFontFamily,
                        color = TextColor.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    //dibujo del grafico de torta (donut chart)
                    Box(
                        modifier = Modifier.size(130.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(110.dp)) {
                            var startAngle = -90f
                            var colorIndex = 0
                            for ((_, percentage) in toolUsagePercentages) {
                                val sweepAngle = (percentage / 100f) * 360f
                                val color = sliceColors[colorIndex % sliceColors.size]
                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
                                )
                                startAngle += sweepAngle
                                colorIndex += 1
                            }
                        }
                        Text(
                            text = "100%",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FigtreeFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = TextColor
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    //leyenda de herramientas con sus porcentajes y tipografia oficial
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var colorIndex = 0
                        for ((toolName, percentage) in toolUsagePercentages) {
                            val color = sliceColors[colorIndex % sliceColors.size]
                            colorIndex += 1

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = toolName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontFamily = FigtreeFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = TextColor,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    text = "${percentage.toInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = FigtreeFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = TextColor.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Stats Screen Preview")
@Composable
fun StatsScreenPreview() {
    val sampleStats = StatsModel(
        totalCheckIns = 14,
        topEmotion = Emotion.HAPPINESS,
        weeklyIntensities = mapOf(
            "Mon" to 4.0f,
            "Tue" to 3.0f,
            "Wed" to 2.0f,
            "Thu" to 4.0f,
            "Fri" to 5.0f,
            "Sat" to 0f,
            "Sun" to 3.5f
        ),
        weeklyEmotions = mapOf(
            "Mon" to Emotion.HAPPINESS,
            "Tue" to Emotion.FEAR,
            "Wed" to Emotion.SADNESS,
            "Thu" to Emotion.ANGER,
            "Fri" to Emotion.SURPRISE,
            "Sun" to Emotion.DISGUST
        ),
        toolUsagePercentages = mapOf(
            "Breathing Pacer" to 42f,
            "Photo of the Day" to 25f,
            "Achievement Jar" to 17f,
            "Blow It Out" to 16f
        ),
        emotionFrequencies = mapOf(
            Emotion.HAPPINESS to 5,
            Emotion.SURPRISE to 4,
            Emotion.FEAR to 2,
            Emotion.SADNESS to 2,
            Emotion.ANGER to 1
        )
    )

    AppTheme {
        StatsScreenContent(
            stats = sampleStats,
            isLoading = false
        )
    }
}
