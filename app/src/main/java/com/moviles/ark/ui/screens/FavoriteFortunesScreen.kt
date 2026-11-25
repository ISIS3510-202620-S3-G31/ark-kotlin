package com.moviles.ark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.moviles.ark.domain.models.FortuneCardModel
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.ui.components.FortuneBackdrop
import com.moviles.ark.ui.components.FortuneHeader
import com.moviles.ark.ui.components.FortunePaperColor
import com.moviles.ark.ui.components.FortuneSnackbarHost
import com.moviles.ark.ui.components.liquidGlass
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.FortuneCookieViewModel
import com.moviles.ark.ui.viewmodels.FortuneUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//conecta la coleccion con el viewmodel
@Composable
fun FavoriteFortunesRoute(onBack: () -> Unit, viewModel: FortuneCookieViewModel = viewModel(factory = FortuneCookieViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    //cuando la imagen esta lista se abre el menu de compartir
    LaunchedEffect(uiState.shareUri) {
        val uri = uiState.shareUri
        if (uri != null) {
            openShareSheet(context, uri)
            viewModel.onShareHandled()
        }
    }
    FavoriteFortunesScreen(uiState = uiState, onBack = onBack, onRemoveFavorite = { viewModel.toggleFavorite(it) }, onShareCard = { viewModel.shareCard(it) }, onClearSnackbar = { viewModel.clearSnackbar() })
}

//coleccion, frases guardadas con el corazon e imagenes compartidas
@Composable
fun FavoriteFortunesScreen(uiState: FortuneUiState, onBack: () -> Unit, onRemoveFavorite: (FortuneModel) -> Unit, onShareCard: (FortuneCardModel) -> Unit, onClearSnackbar: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var toRemove by remember { mutableStateOf<FortuneModel?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onClearSnackbar()
        }
    }

    //confirmacion antes de quitar una frase
    val removing = toRemove
    if (removing != null) {
        val title = @Composable { Text(text = "Remove this phrase?", style = MaterialTheme.typography.headlineSmall) }
        val body = @Composable { Text(text = "It will leave My collection on this phone and in your account.") }
        val confirm = @Composable { TextButton(onClick = { onRemoveFavorite(removing); toRemove = null }) { Text(text = "Remove", color = AccentColor) } }
        val dismiss = @Composable { TextButton(onClick = { toRemove = null }) { Text(text = "Keep it", color = TextColor) } }
        AlertDialog(onDismissRequest = { toRemove = null }, confirmButton = confirm, dismissButton = dismiss, title = title, text = body, containerColor = BackgroundColor, titleContentColor = TextColor, textContentColor = TextColor)
    }

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0), snackbarHost = { FortuneSnackbarHost(snackbarHostState) }) { padding ->
        FortuneBackdrop(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                FortuneHeader(title = "My collection", onBack = onBack)
                CollectionTabs(selected = selectedTab, phrases = uiState.favoriteFortunes.size, images = uiState.savedCards.size, onSelect = { selectedTab = it })
                val hint = if (selectedTab == 0) "Phrases you kept with the heart" else "Images you shared, also in your phone gallery"
                Text(text = hint, style = MaterialTheme.typography.bodySmall, color = TextColor.copy(alpha = 0.75f), modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
                if (selectedTab == 0) PhrasesList(uiState.favoriteFortunes, onRemove = { toRemove = it }) else ImagesGrid(uiState.savedCards, onShareCard)
            }
        }
    }
}

//selector de vidrio entre frases e imagenes
@Composable
private fun CollectionTabs(selected: Int, phrases: Int, images: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth().liquidGlass(RoundedCornerShape(26.dp)).padding(4.dp)) {
        listOf("Kept ($phrases)", "Shared ($images)").forEachIndexed { index, label ->
            val color = if (selected == index) PrimaryColor else Color.Transparent
            Box(modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(color).clickable(role = Role.Tab, onClick = { onSelect(index) }), contentAlignment = Alignment.Center) {
                Text(text = label, style = MaterialTheme.typography.labelLarge, color = TextColor)
            }
        }
    }
}

//lista de frases guardadas
@Composable
private fun PhrasesList(favorites: List<FortuneModel>, onRemove: (FortuneModel) -> Unit) {
    if (favorites.isEmpty()) {
        EmptyCollection("Nothing kept yet. Open a cookie and tap Keep to have it here.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        items(favorites, key = { it.id }) { item -> SavedPhrase(item = item, onRemove = { onRemove(item) }) }
    }
}

//frase guardada como el papelito de la galleta, con la fecha y el corazon para quitarla
@Composable
private fun SavedPhrase(item: FortuneModel, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    val lines = Modifier.drawBehind {
        val inset = 10.dp.toPx()
        drawLine(PrimaryColor.copy(alpha = 0.35f), Offset(inset, inset), Offset(size.width - inset, inset), 1.dp.toPx())
        drawLine(PrimaryColor.copy(alpha = 0.35f), Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset), 1.dp.toPx())
    }
    Column(modifier = Modifier.fillMaxWidth().shadow(6.dp, shape, ambientColor = TextColor, spotColor = TextColor).background(FortunePaperColor, shape).then(lines).padding(start = 22.dp, end = 8.dp, top = 22.dp, bottom = 10.dp)) {
        Text(text = item.quote, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 17.sp, lineHeight = 24.sp), color = TextColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(end = 14.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Kept ${formatDay(item.savedAt)}", style = MaterialTheme.typography.bodySmall, color = TextColor.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(imageVector = Icons.Default.Favorite, contentDescription = "Remove from My collection", tint = AccentColor, modifier = Modifier.size(20.dp))
            }
        }
    }
}

//cuadricula de imagenes, al tocar una se vuelve a compartir
@Composable
private fun ImagesGrid(cards: List<FortuneCardModel>, onShareCard: (FortuneCardModel) -> Unit) {
    if (cards.isEmpty()) {
        EmptyCollection("No images yet. Open a cookie and tap Share to keep its image here.")
        return
    }
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(cards, key = { it.id }) { card -> SavedImage(card = card, onShare = { onShareCard(card) }) }
    }
}

//imagen en un marco de vidrio, con la fecha y si ya esta respaldada en la cuenta
@Composable
private fun SavedImage(card: FortuneCardModel, onShare: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClickLabel = "Share again", onClick = onShare).padding(6.dp)) {
        AsyncImage(model = card.imageUri, contentDescription = card.quote, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(13.dp)).background(FortunePaperColor))
        Row(modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = formatDay(card.savedAt), style = MaterialTheme.typography.bodySmall, color = TextColor, modifier = Modifier.weight(1f))
            val cloudIcon = if (card.isBackedUp) Icons.Default.CloudDone else Icons.Default.CloudUpload
            val cloudText = if (card.isBackedUp) "Backed up to your account" else "Will back up when you're online"
            Icon(imageVector = cloudIcon, contentDescription = cloudText, tint = if (card.isBackedUp) SecondaryColor else TextColor.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = TextColor.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        }
    }
}

//mensaje cuando no hay nada guardado, dice que hacer para llenarlo
@Composable
private fun EmptyCollection(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 64.dp), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.8f), textAlign = TextAlign.Center)
    }
}

//fecha corta en ingles, por ejemplo nov 24
private fun formatDay(millis: Long): String = SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(millis))
