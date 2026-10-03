package com.moviles.ark.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.ui.components.MoodSelectorComponent
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.SuccessColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.FeedbackUiState
import com.moviles.ark.ui.viewmodels.FeedbackViewModel

@Composable
fun PostToolFeedbackRoute(
    toolId: String = "",
    viewModel: FeedbackViewModel = viewModel(factory = FeedbackViewModel.Factory),
    onFeedbackSubmitted: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(toolId) {
        if (toolId.isNotBlank()) {
            viewModel.setToolId(toolId)
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onFeedbackSubmitted()
        }
    }

    PostToolFeedbackScreen(
        uiState = uiState,
        onRatingChange = viewModel::onRatingChange,
        onCommentChange = viewModel::onCommentChange,
        onEmotionToggle = viewModel::onEmotionToggle,
        onIntensityChange = viewModel::onIntensityChange,
        onSubmitClick = { viewModel.saveFeedback(onSavedCallback = onFeedbackSubmitted) },
        onDismiss = onDismiss
    )
}

@Composable
fun PostToolFeedbackScreen(
    uiState: FeedbackUiState,
    onRatingChange: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onEmotionToggle: (Emotion) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onSubmitClick: () -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = BackgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            HeaderSection(onDismiss = onDismiss)

            Spacer(modifier = Modifier.height(20.dp))

            // Completion Banner
            CompletionBannerCard()

            Spacer(modifier = Modifier.height(24.dp))

            // Satisfaction Rating (1-5 stars / emojis)
            SatisfactionRatingSection(
                rating = uiState.rating,
                onRatingChange = onRatingChange
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Embedded Post-Tool Mood Check-in Component
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = TextColor.copy(alpha = 0.05f)),
                border = BorderStroke(1.dp, TextColor.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MoodSelectorComponent(
                        selectedEmotions = uiState.selectedEmotions,
                        intensity = uiState.intensity,
                        onEmotionToggle = onEmotionToggle,
                        onIntensityChange = onIntensityChange,
                        activeEmotion = uiState.activeEmotion
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Optional Reflection / Comment Text Field
            OutlinedTextField(
                value = uiState.comment,
                onValueChange = onCommentChange,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextColor),
                label = { Text("Reflection / Comments (optional)", style = LocalTextStyle.current.copy(fontFamily = FigtreeFontFamily)) },
                placeholder = { Text("Write any thoughts or insights from this session...", style = LocalTextStyle.current.copy(fontFamily = FigtreeFontFamily)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SecondaryColor,
                    unfocusedBorderColor = TextColor.copy(alpha = 0.3f),
                    focusedLabelColor = TextColor,
                    unfocusedLabelColor = TextColor.copy(alpha = 0.7f),
                    cursorColor = TextColor
                )
            )

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    border = BorderStroke(1.dp, SecondaryColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)
                ) {
                    Text("Skip", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onSubmitClick,
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .weight(1.5f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = TextColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Feedback",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SecondaryColor)
                .clickable(role = Role.Button, onClickLabel = "Back", onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "Session Complete!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextColor
            )
            Text(
                text = "How was your experience?",
                style = MaterialTheme.typography.bodyMedium,
                color = TextColor.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CompletionBannerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2E3CD)),
        border = BorderStroke(1.dp, Color(0xFFE2CEB5))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SuccessColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = TextColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Great job finishing your exercise!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextColor
                )
                Text(
                    text = "Your feedback helps tailor future suggestions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SatisfactionRatingSection(
    rating: Int,
    onRatingChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Rate your satisfaction",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextColor
        )
        Text(
            text = "Tap a rating from 1 to 5 stars",
            style = MaterialTheme.typography.bodySmall,
            color = TextColor.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val emojis = listOf("😖", "😕", "😐", "🙂", "🤩")
            val labels = listOf("1", "2", "3", "4", "5")

            (1..5).forEach { star ->
                val isSelected = star <= rating
                val isExactSelected = star == rating
                val emoji = emojis[star - 1]

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onRatingChange(star) }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isExactSelected -> PrimaryColor
                                    isSelected -> PrimaryColor.copy(alpha = 0.4f)
                                    else -> TextColor.copy(alpha = 0.08f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 26.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (isSelected) PrimaryColor else TextColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = labels[star - 1],
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) TextColor else TextColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Post Tool Feedback Screen Preview")
@Composable
fun PostToolFeedbackScreenPreview() {
    AppTheme {
        PostToolFeedbackScreen(
            uiState = FeedbackUiState(
                toolId = "custom_breathing",
                rating = 4,
                comment = "Felt very relaxed afterwards!",
                selectedEmotions = setOf(Emotion.HAPPINESS)
            ),
            onRatingChange = {},
            onCommentChange = {},
            onEmotionToggle = {},
            onIntensityChange = {},
            onSubmitClick = {},
            onDismiss = {}
        )
    }
}
