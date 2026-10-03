package com.moviles.ark.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moviles.ark.ui.viewmodels.FeedbackUiState

@Composable
fun FeedbackScreen(
    uiState: FeedbackUiState,
    onRatingChange: (Float) -> Unit = {},
    onReflectionChange: (String) -> Unit = {},
    onSubmitClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    PostToolFeedbackScreen(
        uiState = uiState,
        onRatingChange = { onRatingChange(it.toFloat()) },
        onCommentChange = onReflectionChange,
        onEmotionToggle = {},
        onIntensityChange = {},
        onSubmitClick = onSubmitClick,
        modifier = modifier
    )
}
