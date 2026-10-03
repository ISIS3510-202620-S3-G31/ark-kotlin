package com.moviles.ark.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor

//asigna su emoji a cada emocion
fun getEmotionEmoji(emotion: Emotion): String {
    when (emotion) {
        Emotion.HAPPINESS -> return "😊"
        Emotion.FEAR -> return "😨"
        Emotion.SADNESS -> return "😢"
        Emotion.ANGER -> return "😡"
        Emotion.SURPRISE -> return "😲"
        Emotion.DISGUST -> return "🤢"
    }
}

//asigna su color tematico
fun getEmotionColor(emotion: Emotion): Color {
    when (emotion) {
        Emotion.HAPPINESS -> return Color(0xFF5DBB63)
        Emotion.FEAR -> return PrimaryColor
        Emotion.SADNESS -> return SecondaryColor
        Emotion.ANGER -> return AccentColor
        Emotion.SURPRISE -> return SecondaryColor
        Emotion.DISGUST -> return Color(0xFF5DBB63)
    }
}

//componente visual de seleccion emocional con halos diferenciados y slider
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodSelectorComponent(
    selectedEmotions: Set<Emotion>,
    intensity: Float,
    onEmotionToggle: (Emotion) -> Unit,
    onIntensityChange: (Float) -> Unit,
    activeEmotion: Emotion? = null,
    modifier: Modifier = Modifier
) {
    val orderedEmotions = listOf(
        Emotion.HAPPINESS,
        Emotion.FEAR,
        Emotion.SADNESS,
        Emotion.ANGER,
        Emotion.SURPRISE,
        Emotion.DISGUST
    )
    val currentActive = activeEmotion ?: selectedEmotions.lastOrNull() ?: Emotion.HAPPINESS
    val activeColor = getEmotionColor(currentActive)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "How are you feeling?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Select emotions and adjust intensity",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (emotion in orderedEmotions) {
                val isSelected = selectedEmotions.contains(emotion)
                val isActive = isSelected && (emotion == currentActive)
                val color = getEmotionColor(emotion)
                val emoji = getEmotionEmoji(emotion)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onEmotionToggle(emotion) }
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .then(
                                if (isActive) {
                                    Modifier.drawBehind {
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    color.copy(alpha = 0.75f),
                                                    color.copy(alpha = 0.28f),
                                                    Color.Transparent
                                                ),
                                                radius = 34.dp.toPx()
                                            ),
                                            radius = 34.dp.toPx()
                                        )
                                    }
                                } else if (isSelected) {
                                    Modifier.drawBehind {
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    color.copy(alpha = 0.35f),
                                                    Color.Transparent
                                                ),
                                                radius = 24.dp.toPx()
                                            ),
                                            radius = 24.dp.toPx()
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .clip(CircleShape)
                            .background(
                                when {
                                    isActive -> color.copy(alpha = 0.32f)
                                    isSelected -> color.copy(alpha = 0.15f)
                                    else -> Color.White.copy(alpha = 0.28f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 27.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = emotion.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isActive) FontWeight.Bold else if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) TextColor else if (isSelected) TextColor.copy(alpha = 0.8f) else TextColor.copy(alpha = 0.5f),
                        fontSize = 10.5.sp
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectedEmotions.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
            ) {
                Slider(
                    value = intensity,
                    onValueChange = onIntensityChange,
                    valueRange = 1f..5f,
                    steps = 3,
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .drawBehind {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                activeColor.copy(alpha = 0.75f),
                                                activeColor.copy(alpha = 0.3f),
                                                Color.Transparent
                                            ),
                                            radius = 24.dp.toPx()
                                        ),
                                        radius = 24.dp.toPx()
                                    )
                                }
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(Color.White, activeColor)))
                                .border(2.5.dp, Color.White, CircleShape)
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier
                                .height(14.dp)
                                .shadow(2.dp, RoundedCornerShape(7.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(7.dp)),
                            colors = SliderDefaults.colors(
                                activeTrackColor = activeColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.55f)
                            ),
                            thumbTrackGapSize = 0.dp
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1 (Mild)", style = MaterialTheme.typography.bodySmall, color = TextColor.copy(alpha = 0.6f), fontSize = 11.sp)
                    Text("3 (Moderate)", style = MaterialTheme.typography.bodySmall, color = TextColor.copy(alpha = 0.6f), fontSize = 11.sp)
                    Text("5 (Intense)", style = MaterialTheme.typography.bodySmall, color = TextColor.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
        }
    }
}

//preview interactivo
@Preview(showBackground = true, name = "Mood Selector Component Preview")
@Composable
fun MoodSelectorComponentPreview() {
    AppTheme({
        val selected = remember { androidx.compose.runtime.mutableStateOf(setOf(Emotion.HAPPINESS)) }
        val intensity = remember { androidx.compose.runtime.mutableFloatStateOf(3f) }
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            MoodSelectorComponent(
                selectedEmotions = selected.value,
                intensity = intensity.value,
                onEmotionToggle = { emotion ->
                    selected.value = if (selected.value.contains(emotion)) {
                        selected.value - emotion
                    } else {
                        selected.value + emotion
                    }
                },
                onIntensityChange = { intensity.value = it }
            )
        }
    })
}