package com.moviles.ark.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.models.InsightModel
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor

//tarjeta compacta y horizontal del hallazgo util: 3 lineas de 20 sp + 20 dp de margen, menos de 90 dp
//toda la tarjeta es tocable: abre el catalogo filtrado con la categoria de la emocion detectada
@Composable
fun UsefulInsightComponent(
    insight: InsightModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val category = insight.toolCategory()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 88.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = PrimaryColor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, TextColor.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            //emoji de la emocion detectada; si no hay emocion se usa el bombillo
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        insight.emotion?.let { getEmotionColor(it).copy(alpha = 0.25f) }
                            ?: PrimaryColor.copy(alpha = 0.18f)
                    )
                    .border(1.dp, TextColor.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val emotion = insight.emotion
                if (emotion != null) {
                    Text(text = getEmotionEmoji(emotion), style = MaterialTheme.typography.headlineSmall)
                } else {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = PrimaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = insight.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                //a donde lleva el toque: la categoria ya filtrada en el catalogo
                Text(
                    text = category?.let { "Try ${it.displayName} tools" } ?: "Explore tools",
                    style = MaterialTheme.typography.labelLarge,
                    color = SecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = category?.let { "Open ${it.displayName} tools" } ?: "Open tools",
                tint = TextColor.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UsefulInsightComponentPreview() {
    val sampleInsight = InsightModel(
        title = "Sadness has been around",
        message = "Sadness showed up in 4 of your 6 check-ins this week.",
        advice = "Naming a feeling is the first step.",
        actionLabel = "Find a tool",
        emotion = Emotion.SADNESS
    )

    AppTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            UsefulInsightComponent(insight = sampleInsight)
        }
    }
}
