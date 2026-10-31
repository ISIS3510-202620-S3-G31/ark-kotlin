package com.moviles.ark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviles.ark.domain.composite.MoodFactory
import com.moviles.ark.domain.composite.SingleEmotion
import com.moviles.ark.domain.models.MoodCheckInModel
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor

//resumen de solo lectura del check-in del dia: cada emocion con su intensidad, la nota y la ciudad
//lo usan el dialogo del home y la pantalla del check-in, asi se ve igual en los dos
@Composable
fun CheckInSummaryComponent(
    checkIn: MoodCheckInModel?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val parts = checkIn?.mood?.let { MoodFactory.partsOf(it) }.orEmpty()
        if (parts.isEmpty()) {
            Text(
                text = "Daily check-in completed",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FigtreeFontFamily,
                color = TextColor.copy(alpha = 0.7f)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                parts.forEach { EmotionIntensityRow(it) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TextColor.copy(alpha = 0.06f))
                .border(1.dp, TextColor.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "Note",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FigtreeFontFamily,
                fontWeight = FontWeight.Bold,
                color = TextColor.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            val note = checkIn?.note.orEmpty()
            Text(
                text = note.ifBlank { "No note added for today." },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FigtreeFontFamily,
                color = if (note.isNotBlank()) TextColor else TextColor.copy(alpha = 0.45f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        //solo la ciudad, nunca las coordenadas
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(TextColor.copy(alpha = 0.06f))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = "Location",
                tint = PrimaryColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = checkIn?.locationLabel() ?: MoodCheckInModel.NO_LOCATION_LABEL,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FigtreeFontFamily,
                color = TextColor.copy(alpha = 0.75f),
                fontSize = 12.sp
            )
        }
    }
}

//una emocion con su propia intensidad: barra de 5 pasos en el color de la emocion, como el slider del check-in
@Composable
private fun EmotionIntensityRow(part: SingleEmotion) {
    val color = getEmotionColor(part.emotion)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = getEmotionEmoji(part.emotion), fontSize = 20.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = emotionLabel(part.emotion),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FigtreeFontFamily,
                fontWeight = FontWeight.Bold,
                color = TextColor
            )
            Text(
                text = "${intensityLabel(part.intensity)} · ${part.intensity}/${SingleEmotion.MAX_INTENSITY}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FigtreeFontFamily,
                color = TextColor.copy(alpha = 0.65f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (step in SingleEmotion.MIN_INTENSITY..SingleEmotion.MAX_INTENSITY) {
                Box(
                    modifier = Modifier
                        .size(width = 12.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (step <= part.intensity) color else TextColor.copy(alpha = 0.12f))
                )
            }
        }
    }
}
