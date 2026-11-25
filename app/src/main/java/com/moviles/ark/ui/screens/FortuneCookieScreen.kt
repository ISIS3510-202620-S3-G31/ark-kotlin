package com.moviles.ark.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.ui.components.ActiveFortuneCard
import com.moviles.ark.ui.components.FortuneBackdrop
import com.moviles.ark.ui.components.FortuneCookiesRow
import com.moviles.ark.ui.components.FortuneHeader
import com.moviles.ark.ui.components.FortunePaper
import com.moviles.ark.ui.components.FortunePill
import com.moviles.ark.ui.components.FortuneSnackbarHost
import com.moviles.ark.ui.components.RecentFortunesCarousel
import com.moviles.ark.ui.components.liquidGlass
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.DAILY_FORTUNE_CHANCES
import com.moviles.ark.ui.viewmodels.FortuneCookieViewModel
import com.moviles.ark.ui.viewmodels.FortunePhase
import com.moviles.ark.ui.viewmodels.FortuneUiState

//conecta el viewmodel con la pantalla
@Composable
fun FortuneCookieRoute(onBack: (wasCompleted: Boolean, durationSeconds: Int) -> Unit, onOpenFavorites: () -> Unit, viewModel: FortuneCookieViewModel = viewModel(factory = FortuneCookieViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    //salir con atras o con i'm done hace lo mismo, si se abrio una galleta se pasa al feedback
    val finish = {
        val (completed, duration) = viewModel.finishSession()
        onBack(completed, duration)
    }
    BackHandler { finish() }

    //cuando la imagen esta lista se abre el menu de compartir del telefono
    LaunchedEffect(uiState.shareUri) {
        val uri = uiState.shareUri
        if (uri != null) {
            openShareSheet(context, uri)
            viewModel.onShareHandled()
        }
    }

    FortuneCookieScreen(uiState = uiState, onBack = finish, onDone = finish, onOpenFavorites = onOpenFavorites, onCrackCookie = { viewModel.crackCookie(it) }, onToggleFavorite = { viewModel.toggleFavorite(it) }, onOpenRecent = { viewModel.openRecent(it) }, onCloseRecent = { viewModel.closeRecent() }, onShare = { viewModel.shareOpened() }, onClearSnackbar = { viewModel.clearSnackbar() })
}

//abre el menu de compartir del telefono con la imagen
fun openShareSheet(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = "image/png"
    intent.putExtra(Intent.EXTRA_STREAM, uri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share your fortune"))
}

//pantalla de la galleta, se elige una, se abre en el centro y se lee el papelito
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FortuneCookieScreen(uiState: FortuneUiState, onBack: () -> Unit, onDone: () -> Unit, onOpenFavorites: () -> Unit, onCrackCookie: (Int) -> Unit, onToggleFavorite: (FortuneModel) -> Unit, onOpenRecent: (FortuneModel) -> Unit, onCloseRecent: () -> Unit, onShare: () -> Unit, onClearSnackbar: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onClearSnackbar()
        }
    }
    val choosing = uiState.phase == FortunePhase.CHOOSING
    val opened = uiState.opened

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0), snackbarHost = { FortuneSnackbarHost(snackbarHostState) }) { padding ->
        FortuneBackdrop(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
                FortuneHeader(title = "Fortune cookie", onBack = onBack, trailing = { CollectionButton(count = uiState.favoriteFortunes.size, onClick = onOpenFavorites) })
                AnimatedVisibility(visible = choosing, enter = fadeIn(), exit = fadeOut() + shrinkVertically()) {
                    ChancesIntro(remaining = uiState.remainingChances)
                }
                FortuneCookiesRow(phase = uiState.phase, chosenIndex = uiState.chosenIndex, hasChances = uiState.remainingChances != 0, onCrackCookie = onCrackCookie)
                if (uiState.phase == FortunePhase.REVEALED && opened != null) {
                    ActiveFortuneCard(opened = opened, isSharing = uiState.isSharing, onToggleFavorite = { onToggleFavorite(opened.fortune) }, onShare = onShare, onDone = onDone, modifier = Modifier.padding(bottom = 32.dp))
                }
                AnimatedVisibility(visible = choosing, enter = fadeIn(), exit = fadeOut() + shrinkVertically()) {
                    RecentFortunesCarousel(recents = uiState.recentFortunes, onSelectFortune = onOpenRecent, modifier = Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }

    //frase reciente para leerla otra vez y guardarla si se olvido
    val preview = uiState.previewFortune
    if (preview != null) {
        ModalBottomSheet(onDismissRequest = onCloseRecent, containerColor = BackgroundColor, contentColor = TextColor) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FortunePaper(quote = preview.quote)
                Spacer(modifier = Modifier.height(20.dp))
                FortunePill(text = if (preview.isFavorite) "Kept" else "Keep", icon = if (preview.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, iconTint = AccentColor, onClick = { onToggleFavorite(preview) })
            }
        }
    }
}

//indicacion corta y las galletas que quedan hoy como puntos, mientras carga no muestra los puntos
@Composable
private fun ChancesIntro(remaining: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
        val title = if (remaining != 0) "Pick a cookie and take a moment with what it says." else "You opened all your cookies for today."
        Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp), color = TextColor)
        if (remaining < 0) return@Column
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(DAILY_FORTUNE_CHANCES) { i ->
                Box(modifier = Modifier.padding(end = 6.dp).size(10.dp).clip(CircleShape).background(if (i < remaining) PrimaryColor else TextColor.copy(alpha = 0.15f)))
            }
            Spacer(modifier = Modifier.width(6.dp))
            val label = if (remaining > 0) "$remaining of $DAILY_FORTUNE_CHANCES cookies left today" else "New cookies arrive tomorrow"
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextColor.copy(alpha = 0.75f))
        }
    }
}

//boton de vidrio arriba a la derecha que lleva a la coleccion, con el corazon y cuantas frases hay
@Composable
private fun CollectionButton(count: Int, onClick: () -> Unit) {
    Row(modifier = Modifier.height(44.dp).liquidGlass(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClickLabel = "Open My collection", onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Default.Favorite, contentDescription = "My collection", tint = AccentColor, modifier = Modifier.size(20.dp))
        if (count > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "$count", style = MaterialTheme.typography.labelLarge, color = TextColor)
        }
    }
}
