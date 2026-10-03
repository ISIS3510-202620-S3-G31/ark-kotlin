package com.moviles.ark.ui.screens

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
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.models.StatsModel
import com.moviles.ark.ui.components.getEmotionColor
import com.moviles.ark.ui.components.getEmotionEmoji
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.StatsViewModel

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    //recarga automaticamente al entrar a la pantalla para mostrar check-ins recientes
    LaunchedEffect(Unit) {
        viewModel.loadStats()
    }

    StatsScreenContent(
        stats = uiState.stats,
        isLoading = uiState.isLoading,
        onRefresh = { viewModel.refresh() }
    )
}

//vista desacoplada sin viewmodel para permitir renderizado fluido en preview
@Composable
fun StatsScreenContent(
    stats: StatsModel?,
    isLoading: Boolean,
    onRefresh: () -> Unit = {}
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
            //barra superior con titulo principal centrado y boton de refresco
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 14.dp)
            ) {
                Text(
                    text = "Wellbeing Stats",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextColor,
                    modifier = Modifier.align(Alignment.Center)
                )

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .align(Alignment.CenterEnd)
                        .shadow(4.dp, CircleShape, ambientColor = Color(0x35351B08), spotColor = Color(0x35351B08))
                        .clip(CircleShape)
                        .background(Color(0xFFFFF5E6))
                        .border(1.2.dp, Color(0xFFE8D3B9), CircleShape)
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

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryColor)
                }
            } else if (stats == null || stats.totalCheckIns == 0) {
                StatsEmptyView()
            } else {
                StatsMainView(stats = stats)
            }
        }
    }
}

//vista cuando el usuario aun no ha registrado check-ins ni herramientas
@Composable
private fun StatsEmptyView() {
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E6)),
            border = BorderStroke(1.2.dp, Color(0xFFE8D3B9))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(PrimaryColor.copy(alpha = 0.18f))
                        .border(1.2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoGraph,
                        contentDescription = null,
                        tint = PrimaryColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No check-ins or tool usage yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Start your first check-in or explore wellbeing tools to see your emotional journey and stats here!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun StatsMainView(
    stats: StatsModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        //capsulas de resumen directo y limpio (sin mascota ni textos largos)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                //capsula 1 (izquierda): top feeling con halo difuminado del patron composite y relieve
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(5.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x30351B08), spotColor = Color(0x30351B08)),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFFF5E6),
                    border = BorderStroke(1.2.dp, Color(0xFFE8D3B9))
                ) {
                    val emotion = stats.topEmotion ?: Emotion.HAPPINESS
                    val color = getEmotionColor(emotion)
                    val emoji = getEmotionEmoji(emotion)
                    val emotionName = emotion.name.lowercase().replaceFirstChar { it.uppercase() }

                    Row(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFFFF9EE), Color(0xFFFEEDD8))
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
                                .border(1.2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 21.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Top Feeling",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextColor.copy(alpha = 0.65f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = emotionName,
                                style = MaterialTheme.typography.titleMedium,
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
                    color = Color(0xFFFFF5E6),
                    border = BorderStroke(1.2.dp, Color(0xFFE8D3B9))
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFFFF9EE), Color(0xFFFEEDD8))
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
                                .border(1.2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
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
                                color = TextColor.copy(alpha = 0.65f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${stats.totalCheckIns}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextColor,
                                fontSize = 17.sp
                            )
                        }
                    }
                }
            }
        }

        //grafico de barras: journey semanal de emociones con relieve y escala 1 a 5
        item {
            WeeklyEmotionJourneyCard(
                weeklyIntensities = stats.weeklyIntensities,
                weeklyEmotions = stats.weeklyEmotions
            )
        }

        //grafico de torta: frecuencia de herramientas con relieve
        item {
            ToolsUsagePieChartCard(toolUsagePercentages = stats.toolUsagePercentages)
        }
    }
}

//grafico de barras para el journey emocional de la semana con escala visible de 1 a 5 y relieve
@Composable
private fun WeeklyEmotionJourneyCard(
    weeklyIntensities: Map<String, Float>,
    weeklyEmotions: Map<String, Emotion>
) {
    val dayLabels = if (weeklyIntensities.isNotEmpty()) weeklyIntensities.keys.toList() else listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x35351B08), spotColor = Color(0x35351B08)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E6)),
        border = BorderStroke(1.2.dp, Color(0xFFE8D3B9))
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
                        text = "Weekly Emotion Journey",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                    Text(
                        text = "Dominant emotion per day and its intensity",
                        style = MaterialTheme.typography.bodyLarge,
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
                        val emoji = if (emotion != null) getEmotionEmoji(emotion) else "✨"
                        val intensityRatio = (intensity / 5f).coerceIn(0f, 1f)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (hasEntry) {
                                //emoji con halo difuminado idéntico al mood check-in
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
                                    .height((90 * intensityRatio).coerceAtLeast(8f).dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (hasEntry) barColor else Color.Black.copy(alpha = 0.08f)
                                    )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 11.5.sp,
                                fontWeight = if (hasEntry) FontWeight.Bold else FontWeight.Medium,
                                color = TextColor.copy(alpha = if (hasEntry) 0.9f else 0.45f)
                            )
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E6)),
        border = BorderStroke(1.2.dp, Color(0xFFE8D3B9))
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
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                    Text(
                        text = "Historical distribution by % of tool usage",
                        style = MaterialTheme.typography.bodyLarge,
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
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                //leyenda de herramientas con sus porcentajes
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
                                    fontWeight = FontWeight.Medium,
                                    color = TextColor,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "${percentage.toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
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
        )
    )

    AppTheme {
        StatsScreenContent(
            stats = sampleStats,
            isLoading = false
        )
    }
}
