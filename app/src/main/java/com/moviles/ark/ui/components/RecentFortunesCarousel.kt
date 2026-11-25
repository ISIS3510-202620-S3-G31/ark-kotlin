package com.moviles.ark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.TextColor

//las ultimas 5 frases abiertas como papelitos sueltos, al tocar uno se lee otra vez sin gastar galletas
@Composable
fun RecentFortunesCarousel(recents: List<FortuneModel>, onSelectFortune: (FortuneModel) -> Unit, modifier: Modifier = Modifier) {
    if (recents.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "Your last fortunes", style = MaterialTheme.typography.headlineSmall, color = TextColor, modifier = Modifier.padding(horizontal = 24.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(recents, key = { _, item -> item.id }) { index, item ->
                val shape = RoundedCornerShape(4.dp)
                //cada papelito queda un poco torcido, como dejado sobre la mesa
                val tilt = if (index % 2 == 0) -2.5f else 2f
                Box(modifier = Modifier.width(156.dp).height(96.dp).graphicsLayer { rotationZ = tilt }.shadow(6.dp, shape, ambientColor = TextColor, spotColor = TextColor).clip(shape).background(FortunePaperColor).clickable(role = Role.Button, onClickLabel = "Read again") { onSelectFortune(item) }.padding(12.dp)) {
                    Text(text = item.quote, style = MaterialTheme.typography.bodySmall, color = TextColor, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    if (item.isFavorite) Icon(imageVector = Icons.Default.Favorite, contentDescription = "Kept phrase", tint = AccentColor, modifier = Modifier.align(Alignment.BottomEnd).size(14.dp))
                }
            }
        }
    }
}
