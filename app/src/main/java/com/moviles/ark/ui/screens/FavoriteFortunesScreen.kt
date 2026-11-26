package com.moviles.ark.ui.screens

import com.moviles.ark.ui.theme.BackgroundColor
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import com.moviles.ark.ui.components.drawFortunePaper
import com.moviles.ark.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.Dp

import com.moviles.ark.ui.theme.SecondaryColor

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.foundation.border

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.graphics.lerp

import androidx.compose.animation.animateColorAsState

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.moviles.ark.ui.components.FortuneMutedText
import com.moviles.ark.ui.components.FortunePaperColor
import com.moviles.ark.ui.components.FortuneSnackbarHost
import com.moviles.ark.ui.components.liquidGlass
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.FortuneCookieViewModel
import com.moviles.ark.ui.viewmodels.FortuneUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//conecta la coleccion con el viewmodel, initialTab es 0 para kept y 1 para shared
@Composable
fun FavoriteFortunesRoute(onBack: () -> Unit, initialTab: Int = 0, viewModel: FortuneCookieViewModel = viewModel(factory = FortuneCookieViewModel.Factory)) {
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
    FavoriteFortunesScreen(uiState = uiState, initialTab = initialTab, onBack = onBack, onRemoveFavorite = { viewModel.toggleFavorite(it) }, onShareFortune = { viewModel.shareFortune(it) }, onShareCard = { viewModel.shareCard(it) }, onDeleteCard = { viewModel.deleteCard(it) }, onClearSnackbar = { viewModel.clearSnackbar() })
}

//coleccion con dos partes separadas, las frases que se guardaron con el corazon y las imagenes que se compartieron
@Composable
fun FavoriteFortunesScreen(uiState: FortuneUiState, initialTab: Int, onBack: () -> Unit, onRemoveFavorite: (FortuneModel) -> Unit, onShareFortune: (FortuneModel) -> Unit, onShareCard: (FortuneCardModel) -> Unit, onDeleteCard: (FortuneCardModel) -> Unit, onClearSnackbar: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var phraseToRemove by remember { mutableStateOf<FortuneModel?>(null) }
    var cardToRemove by remember { mutableStateOf<FortuneCardModel?>(null) }
    var cardToView by remember { mutableStateOf<FortuneCardModel?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onClearSnackbar()
        }
    }

    //confirmacion amable antes de quitar una frase
    val phrase = phraseToRemove
    if (phrase != null) {
        FriendlyDialog(title = "Let this fortune go?", body = "It will leave your Kept list. You can keep it again the next time you see it.", confirmText = "Let it go", onConfirm = { onRemoveFavorite(phrase); phraseToRemove = null }, onDismiss = { phraseToRemove = null })
    }
    //confirmacion antes de quitar una imagen
    val card = cardToRemove
    if (card != null) {
        FriendlyDialog(title = "Remove this image?", body = "It will leave your Shared list. The copy in your phone's gallery stays.", confirmText = "Remove", onConfirm = { onDeleteCard(card); cardToRemove = null }, onDismiss = { cardToRemove = null })
    }

    //imagen compartida en grande, con compartir otra vez y cerrar
    val viewing = cardToView
    if (viewing != null) {
        CardViewer(card = viewing, onShare = { onShareCard(viewing) }, onClose = { cardToView = null })
    }

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0), snackbarHost = { FortuneSnackbarHost(snackbarHostState) }) { padding ->
        FortuneBackdrop(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                FortuneHeader(title = "My collection", onBack = onBack)
                CollectionTabs(selected = selectedTab, kept = uiState.favoriteFortunes.size, shared = uiState.savedCards.size, onSelect = { selectedTab = it })
                if (selectedTab == 0) {
                    KeptList(favorites = uiState.favoriteFortunes, onShare = onShareFortune, onRemove = { phraseToRemove = it })
                } else {
                    SharedGrid(cards = uiState.savedCards, onOpen = { cardToView = it }, onShare = onShareCard, onRemove = { cardToRemove = it })
                }
            }
        }
    }
}

//dialogo con la paleta, titulo con la letra de la app y botones claros
@Composable
private fun FriendlyDialog(title: String, body: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val titleText = @Composable { Text(text = title, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 24.sp)) }
    val bodyText = @Composable { Text(text = body, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp)) }
    val confirm = @Composable { TextButton(onClick = onConfirm) { Text(text = confirmText, color = AccentColor, style = MaterialTheme.typography.labelLarge) } }
    val dismiss = @Composable { TextButton(onClick = onDismiss) { Text(text = "Keep it", color = TextColor, style = MaterialTheme.typography.labelLarge) } }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = confirm, dismissButton = dismiss, title = titleText, text = bodyText, containerColor = BackgroundColor, titleContentColor = TextColor, textContentColor = TextColor, shape = RoundedCornerShape(28.dp))
}

//selector entre kept y shared, la pista es naranja suave y la elegida va en naranja lleno
@Composable
private fun CollectionTabs(selected: Int, kept: Int, shared: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(PrimaryColor.copy(alpha = 0.2f)).padding(4.dp)) {
        CollectionTab(label = "Kept", count = kept, icon = Icons.Default.Favorite, isSelected = selected == 0, onClick = { onSelect(0) }, modifier = Modifier.weight(1f))
        CollectionTab(label = "Shared", count = shared, icon = Icons.Default.Share, isSelected = selected == 1, onClick = { onSelect(1) }, modifier = Modifier.weight(1f))
    }
}

//una pestana del selector con icono, nombre y cuantas hay
@Composable
private fun CollectionTab(label: String, count: Int, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background by animateColorAsState(if (isSelected) PrimaryColor else Color.Transparent, label = "tab")
    val content = if (isSelected) BackgroundColor else lerp(PrimaryColor, TextColor, 0.45f)
    Row(modifier = modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(background).clickable(role = Role.Tab, onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = content)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "$count", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = content)
    }
}

//lista de frases guardadas con el corazon
@Composable
private fun KeptList(favorites: List<FortuneModel>, onShare: (FortuneModel) -> Unit, onRemove: (FortuneModel) -> Unit) {
    if (favorites.isEmpty()) {
        EmptyCollection(title = "Nothing kept yet", body = "Open a cookie and tap Keep to save the fortunes you want to remember.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(favorites, key = { it.id }) { item -> KeptFortune(item = item, onShare = { onShare(item) }, onRemove = { onRemove(item) }) }
    }
}

//frase guardada como un papelito de galleta, un poco torcido, con la fecha y botones redondos de color
@Composable
private fun KeptFortune(item: FortuneModel, onShare: () -> Unit, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val paper = Brush.verticalGradient(listOf(FortunePaperColor, lerp(FortunePaperColor, PrimaryColor, 0.1f)))
    //cada papel queda girado distinto, como dejados sobre la mesa
    val tilt = if (item.id % 2 == 0) -0.8f else 0.8f
    Column(modifier = Modifier.fillMaxWidth().graphicsLayer { rotationZ = tilt }.shadow(6.dp, shape, ambientColor = TextColor, spotColor = TextColor).clip(shape).background(paper).border(1.dp, PrimaryColor.copy(alpha = 0.5f), shape).padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp)) {
        Text(text = item.quote, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 19.sp, lineHeight = 26.sp), color = TextColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = formatDay(item.savedAt), style = MaterialTheme.typography.labelMedium, color = FortuneMutedText, modifier = Modifier.weight(1f))
            RoundAction(icon = Icons.Default.Share, label = "Share this fortune", tint = SecondaryColor, onClick = onShare)
            Spacer(modifier = Modifier.width(8.dp))
            RoundAction(icon = Icons.Default.Favorite, label = "Remove from Kept", tint = AccentColor, onClick = onRemove)
        }
    }
}

//boton redondo con el fondo suave de su color
@Composable
private fun RoundAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit, background: Color = tint.copy(alpha = 0.14f), size: Dp = 36.dp) {
    Box(modifier = Modifier.size(size).clip(CircleShape).background(background).clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(size / 2))
    }
}

//cuadricula de imagenes compartidas
@Composable
private fun SharedGrid(cards: List<FortuneCardModel>, onOpen: (FortuneCardModel) -> Unit, onShare: (FortuneCardModel) -> Unit, onRemove: (FortuneCardModel) -> Unit) {
    if (cards.isEmpty()) {
        EmptyCollection(title = "Nothing shared yet", body = "When you share a fortune, its image waits for you here.")
        return
    }
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(cards, key = { it.id }) { card -> SharedImage(card = card, onOpen = { onOpen(card) }, onShare = { onShare(card) }, onRemove = { onRemove(card) }) }
    }
}

//la misma imagen que se comparte, con la fecha y los botones encima abajo; al tocarla se abre en grande
@Composable
private fun SharedImage(card: FortuneCardModel, onOpen: () -> Unit, onShare: () -> Unit, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.8f).shadow(8.dp, shape, ambientColor = PrimaryColor, spotColor = PrimaryColor).clip(shape).background(Brush.linearGradient(listOf(PrimaryColor, AccentColor))).clickable(role = Role.Button, onClickLabel = "See this image", onClick = onOpen)) {
        CardImage(card = card, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        //sombra oscura abajo para que se lea la fecha
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(58.dp).background(Brush.verticalGradient(listOf(Color.Transparent, TextColor.copy(alpha = 0.55f)))))
        Row(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 12.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val cloudIcon = if (card.isBackedUp) Icons.Default.CloudDone else Icons.Default.CloudUpload
            val cloudText = if (card.isBackedUp) "Backed up to your account" else "Backs up when you're online"
            Icon(imageVector = cloudIcon, contentDescription = cloudText, tint = BackgroundColor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(text = formatDay(card.savedAt), style = MaterialTheme.typography.labelMedium, color = BackgroundColor, maxLines = 1, modifier = Modifier.weight(1f))
            RoundAction(icon = Icons.Default.Share, label = "Share again", tint = BackgroundColor, onClick = onShare, background = TextColor.copy(alpha = 0.35f), size = 30.dp)
            Spacer(modifier = Modifier.width(4.dp))
            RoundAction(icon = Icons.Default.DeleteOutline, label = "Remove from Shared", tint = BackgroundColor, onClick = onRemove, background = TextColor.copy(alpha = 0.35f), size = 30.dp)
        }
    }
}

//la imagen compartida: coil la lee de la galeria, la reduce al tamano del espacio y la guarda en su cache lru de memoria
//si la borraron de la galeria se usa la copia de firebase storage, que coil tambien guarda en su cache de disco
@Composable
private fun CardImage(card: FortuneCardModel, contentScale: ContentScale, modifier: Modifier = Modifier) {
    var useRemote by remember(card.id) { mutableStateOf(card.imageUri.isEmpty()) }
    val source = if (useRemote) card.remoteImageUrl else card.imageUri
    if (source.isNullOrEmpty()) return
    AsyncImage(model = source, contentDescription = card.quote, contentScale = contentScale, modifier = modifier, onError = { if (!useRemote && !card.remoteImageUrl.isNullOrEmpty()) useRemote = true })
}

//visor a pantalla completa con fondo cafe de la paleta, la imagen grande y dos botones
@Composable
private fun CardViewer(card: FortuneCardModel, onShare: () -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(TextColor.copy(alpha = 0.88f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose).navigationBarsPadding().statusBarsPadding(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(PrimaryColor, AccentColor)))) {
                    CardImage(card = card, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RoundAction(icon = Icons.Default.Share, label = "Share again", tint = TextColor, onClick = onShare, background = SecondaryColor, size = 52.dp)
                    RoundAction(icon = Icons.Default.Close, label = "Close", tint = TextColor, onClick = onClose, background = BackgroundColor, size = 52.dp)
                }
            }
        }
    }
}

//mensaje cuando no hay nada guardado, invita a llenarlo
@Composable
private fun EmptyCollection(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 24.sp), color = TextColor, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = body, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp), color = FortuneMutedText, textAlign = TextAlign.Center)
    }
}

//fecha corta en ingles, por ejemplo nov 24
private fun formatDay(millis: Long): String = SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(millis))
