package com.moviles.ark.ui.screens

import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween

import androidx.compose.animation.core.animateDpAsState

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.ui.components.FortuneBackdrop
import com.moviles.ark.ui.components.FortuneGlassPaper
import com.moviles.ark.ui.components.FortuneHeader
import com.moviles.ark.ui.components.FortuneMutedText
import com.moviles.ark.ui.components.FortuneOfflineNotice
import com.moviles.ark.ui.components.FortuneRevealActions
import com.moviles.ark.ui.components.FortuneSnackbarHost
import com.moviles.ark.ui.components.FortuneStage
import com.moviles.ark.ui.components.RecentFortunesCarousel
import com.moviles.ark.ui.components.liquidGlass
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.DAILY_FORTUNE_CHANCES
import com.moviles.ark.ui.viewmodels.FortuneCookieViewModel
import com.moviles.ark.ui.viewmodels.FortunePhase
import com.moviles.ark.ui.viewmodels.FortuneUiState

//conecta el viewmodel con la pantalla, onOpenCollection recibe 0 para kept y 1 para shared
@Composable
fun FortuneCookieRoute(onBack: (wasCompleted: Boolean, durationSeconds: Int) -> Unit, onOpenCollection: (Int) -> Unit, viewModel: FortuneCookieViewModel = viewModel(factory = FortuneCookieViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    //salir con atras o con i'm done hace lo mismo, si se abrio una galleta se pasa al feedback
    val finish = {
        val (completed, duration) = viewModel.finishSession()
        onBack(completed, duration)
    }
    //si se esta leyendo una reciente, atras vuelve a las galletas
    BackHandler {
        if (uiState.previewFortune != null) viewModel.closeRecent() else finish()
    }

    //cuando la imagen esta lista se abre el menu de compartir del telefono
    LaunchedEffect(uiState.shareUri) {
        val uri = uiState.shareUri
        if (uri != null) {
            openShareSheet(context, uri)
            viewModel.onShareHandled()
        }
    }

    FortuneCookieScreen(uiState = uiState, onBack = finish, onDone = finish, onOpenCollection = onOpenCollection, onCrackCookie = { viewModel.crackCookie(it) }, onToggleFavorite = { viewModel.toggleFavorite(it) }, onOpenRecent = { viewModel.openRecent(it) }, onCloseRecent = { viewModel.closeRecent() }, onShare = { viewModel.shareFortune(it) }, onClearSnackbar = { viewModel.clearSnackbar() })
}

//abre el menu de compartir del telefono con la imagen
fun openShareSheet(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = "image/png"
    intent.putExtra(Intent.EXTRA_STREAM, uri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share your fortune"))
}

//pantalla de la galleta, arriba el titulo, en el medio la mesa y abajo lo guardado y las recientes
@Composable
fun FortuneCookieScreen(uiState: FortuneUiState, onBack: () -> Unit, onDone: () -> Unit, onOpenCollection: (Int) -> Unit, onCrackCookie: (Int) -> Unit, onToggleFavorite: (FortuneModel) -> Unit, onOpenRecent: (FortuneModel) -> Unit, onCloseRecent: () -> Unit, onShare: (FortuneModel) -> Unit, onClearSnackbar: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onClearSnackbar()
        }
    }
    val preview = uiState.previewFortune
    val choosing = uiState.phase == FortunePhase.CHOOSING && preview == null
    val opened = uiState.opened
    //la frase que se ve en el centro, la abierta o la reciente
    val shownFortune = if (preview != null) preview else opened?.fortune

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0), snackbarHost = { FortuneSnackbarHost(snackbarHostState) }) { padding ->
        FortuneBackdrop(modifier = Modifier.padding(padding)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                //mientras se elige la mesa ocupa casi la mitad, al leer la frase se achica para que todo quede junto
                val choosingHeight = (maxHeight * 0.42f).coerceIn(280.dp, 420.dp)
                val stageHeight by animateDpAsState(if (choosing) choosingHeight else 300.dp, tween(500), label = "stageHeight")
                //alto fijo, sin scroll: arriba el titulo, la mesa y sus botones en el centro del espacio que sobra
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    FortuneHeader(title = "Fortune cookie", onBack = if (preview != null) onCloseRecent else onBack)
                    AnimatedVisibility(visible = choosing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        //lo guardado y lo compartido quedan arriba de las galletas
                        CollectionShortcuts(kept = uiState.favoriteFortunes.size, shared = uiState.savedCards.size, onOpen = onOpenCollection)
                    }
                    //las galletas que quedan se ven tambien al leer la frase, el aviso y la instruccion solo al elegir
                    if (preview == null) ChancesLeft(remaining = uiState.remainingChances, offlineNotice = if (choosing) uiState.offlineNotice else null, showHint = choosing)
                    Spacer(modifier = Modifier.weight(1f))

                    FortuneStage(phase = uiState.phase, chosenIndex = uiState.chosenIndex, hasChances = uiState.remainingChances != 0, previewing = preview != null, onCrackCookie = onCrackCookie, stageHeight = stageHeight) { alpha ->
                        if (shownFortune != null) FortuneGlassPaper(quote = shownFortune.quote, alpha = alpha)
                    }

                    //lo de abajo cambia segun el momento
                    Box(modifier = Modifier.fillMaxWidth().padding(top = if (choosing) 8.dp else 20.dp), contentAlignment = Alignment.TopCenter) {
                        if (preview != null) {
                            FortuneRevealActions(isKept = preview.isFavorite, isSharing = uiState.isSharing, mainText = "Back to cookies", onToggleFavorite = { onToggleFavorite(preview) }, onShare = { onShare(preview) }, onMain = onCloseRecent)
                        } else if (uiState.phase == FortunePhase.REVEALED && opened != null) {
                            //una galleta por visita, despues de leerla se sale
                            FortuneRevealActions(isKept = opened.fortune.isFavorite, isSharing = uiState.isSharing, mainText = "I'm done", onToggleFavorite = { onToggleFavorite(opened.fortune) }, onShare = { onShare(opened.fortune) }, onMain = onDone)
                        } else if (uiState.phase == FortunePhase.CHOOSING) {
                            RecentFortunesCarousel(recents = uiState.recentFortunes, sharedIds = uiState.savedCards.map { it.cookieId }.toSet(), onSelectFortune = onOpenRecent)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

//galletas que quedan hoy como puntos naranjas y, solo sin internet, el aviso de donde salen las galletas
@Composable
private fun ChancesLeft(remaining: Int, offlineNotice: String?, showHint: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        //mientras se lee sharedpreferences no se muestra nada
        if (remaining >= 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                for (i in 0 until DAILY_FORTUNE_CHANCES) {
                    val color = if (i < remaining) PrimaryColor else TextColor.copy(alpha = 0.14f)
                    Box(modifier = Modifier.padding(horizontal = 3.dp).size(8.dp).clip(CircleShape).background(color))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            val label = if (remaining == 0) "No cookies left today. Come back tomorrow." else if (remaining == 1) "1 cookie left today" else "$remaining cookies left today"
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = FortuneMutedText, textAlign = TextAlign.Center)
            if (showHint && remaining > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                //la instruccion, cualquier galleta sirve porque todas son al azar
                Text(text = "Pick one. A little wisdom is waiting inside.", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = PrimaryColor, textAlign = TextAlign.Center)
            }
        }
        if (offlineNotice != null) {
            Spacer(modifier = Modifier.height(12.dp))
            FortuneOfflineNotice(text = offlineNotice)
        }
    }
}

//dos pildoras pequenas para abrir lo guardado con el corazon y lo compartido
@Composable
private fun CollectionShortcuts(kept: Int, shared: Int, onOpen: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        CollectionShortcut(label = "Kept", count = kept, icon = Icons.Default.Favorite, iconTint = AccentColor, onClick = { onOpen(0) })
        CollectionShortcut(label = "Shared", count = shared, icon = Icons.Default.Share, iconTint = SecondaryColor, onClick = { onOpen(1) })
    }
}

//una pildora con icono, nombre y cuantas hay
@Composable
private fun CollectionShortcut(label: String, count: Int, icon: ImageVector, iconTint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.height(40.dp).liquidGlass(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClickLabel = "Open $label", onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = TextColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "$count", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = iconTint)
    }
}
