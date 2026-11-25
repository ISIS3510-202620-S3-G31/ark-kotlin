package com.moviles.ark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor

//las ultimas galletas abiertas con esta cuenta, al tocar una su papelito sale otra vez en el centro de la mesa
//sharedIds son las frases que ya se compartieron, para marcarlas
@Composable
fun RecentFortunesCarousel(recents: List<FortuneModel>, sharedIds: Set<Int>, onSelectFortune: (FortuneModel) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "Recent cookies", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp, lineHeight = 32.sp), color = TextColor, modifier = Modifier.padding(horizontal = 24.dp))
        if (recents.isEmpty()) {
            //la seccion no desaparece, invita a abrir la primera
            Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth().height(84.dp).liquidGlass(RoundedCornerShape(18.dp)).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                Text(text = "No recent cookies yet. The ones you open will wait here.", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp), color = FortuneMutedText, textAlign = TextAlign.Center)
            }
            return@Column
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(recents, key = { it.id }) { item -> RecentFortuneChip(item = item, isShared = item.id in sharedIds, onClick = { onSelectFortune(item) }) }
        }
    }
}

//tarjeta de vidrio con la frase centrada y en la esquina de arriba las marcas de guardada y compartida
@Composable
private fun RecentFortuneChip(item: FortuneModel, isShared: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.width(184.dp).height(96.dp).liquidGlass(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClickLabel = "Read this fortune again", onClick = onClick)) {
        val hasBadges = item.isFavorite || isShared
        Text(text = item.quote, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp), color = TextColor, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(start = 14.dp, end = if (hasBadges) 40.dp else 14.dp))
        if (hasBadges) {
            Column(modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.isFavorite) Badge(icon = Icons.Default.Favorite, tint = AccentColor, label = "Kept")
                if (isShared) Badge(icon = Icons.Default.Share, tint = SecondaryColor, label = "Shared")
            }
        }
    }
}

//circulito de color suave con un icono pequeno
@Composable
private fun Badge(icon: ImageVector, tint: Color, label: String) {
    Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(11.dp))
    }
}
