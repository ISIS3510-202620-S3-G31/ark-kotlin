package com.moviles.ark.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor

//tarjeta visual que muestra la frase de la galleta abierta y sus acciones
@Composable
fun ActiveFortuneCard(
    fortune: FortuneModel,
    sourceBadge: String?,
    isExporting: Boolean,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(1.5.dp, PrimaryColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            //insignia que indica con claridad si vino de internet o de la reserva offline
            if (sourceBadge != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (sourceBadge.contains("Offline")) PrimaryColor.copy(alpha = 0.15f) else SecondaryColor.copy(alpha = 0.15f),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = sourceBadge,
                        color = if (sourceBadge.contains("Offline")) PrimaryColor else SecondaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            //fila con categoria y numero de la suerte
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BackgroundLightColor
                ) {
                    Text(
                        text = fortune.category.uppercase(),
                        color = PrimaryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = SecondaryColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Lucky #${fortune.luckyNumber}",
                        color = SecondaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            //texto de la frase de sabiduria
            Text(
                text = "\"${fortune.quote}\"",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center
                ),
                color = TextColor,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            //fila de acciones guardar favorito y compartir tarjeta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.background(
                        color = if (fortune.isFavorite) AccentColor.copy(alpha = 0.15f) else Color.Transparent,
                        shape = CircleShape
                    )
                ) {
                    Icon(
                        imageVector = if (fortune.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (fortune.isFavorite) AccentColor else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }

                FilledTonalButton(
                    onClick = onShare,
                    enabled = !isExporting,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exporting...", fontSize = 13.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share card",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save & Share", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
