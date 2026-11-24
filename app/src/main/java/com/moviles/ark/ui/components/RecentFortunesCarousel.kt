package com.moviles.ark.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor

//carrusel horizontal para explorar las ultimas galletas abiertas
@Composable
fun RecentFortunesCarousel(
    recents: List<FortuneModel>,
    onSelectFortune: (FortuneModel) -> Unit,
    modifier: Modifier = Modifier
) {
    if (recents.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Recent Cookies",
            color = TextColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(recents, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .width(190.dp)
                        .height(115.dp)
                        .clickable { onSelectFortune(item) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BackgroundLightColor),
                    border = BorderStroke(1.dp, PrimaryColor.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.category,
                                color = PrimaryColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (item.isFavorite) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Favorite",
                                    tint = AccentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "\"${item.quote}\"",
                            color = TextColor,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
