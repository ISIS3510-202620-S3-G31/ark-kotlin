package com.moviles.ark.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.ScreenTopPadding
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.WishLanternView
import com.moviles.ark.ui.viewmodels.WishLanternViewModel

//mensajes cuando no hay internet
const val WISHES_OFFLINE_MESSAGE = "You're offline. Your wishes may not be up to date."
const val EDITOR_OFFLINE_MESSAGE = "You're offline. Your wish will be saved when you're back online."

//entrada del farol de deseos: tiene el viewmodel y decide cual de las tres vistas se muestra
//onBack recibe si el usuario termino la herramienta (chulito) y cuantos segundos la uso
@Composable
fun WishLanternRoute(
    viewModel: WishLanternViewModel = viewModel(factory = WishLanternViewModel.Factory),
    onBack: (wasCompleted: Boolean, durationSeconds: Int) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    //atras: desde el editor o la lista vuelve dentro de la herramienta; desde el cielo sale sin feedback
    val goBack = {
        val mustLeaveTool = viewModel.goBack()
        if (mustLeaveTool) {
            onBack(false, 0)
        }
    }
    BackHandler(onBack = goBack)

    //chulito: termina la herramienta y abre el feedback
    val finish = {
        val durationSeconds = viewModel.finishTool()
        onBack(true, durationSeconds)
    }

    //latencia: se espera al primer dibujo de la pantalla
    LaunchedEffect(Unit) {
        withFrameNanos { }
        viewModel.onScreenRendered()
    }

    if (uiState.currentView == WishLanternView.SKY) {
        WishSkyScreen(
            uiState = uiState,
            onBack = goBack,
            onLanternTapped = viewModel::onLanternTapped,
            onCloseOpenedWish = viewModel::closeOpenedWish,
            onLanternReleased = viewModel::onLanternReleased,
            onWriteWish = viewModel::openEditorForNewWish,
            onSeeAllWishes = viewModel::openList,
            onFinish = finish
        )
    }

    if (uiState.currentView == WishLanternView.EDITOR) {
        WishEditorScreen(
            uiState = uiState,
            onBack = goBack,
            onTextChange = viewModel::onEditorTextChange,
            onSave = viewModel::saveWish
        )
    }

    if (uiState.currentView == WishLanternView.LIST) {
        WishListScreen(
            uiState = uiState,
            onBack = goBack,
            onWriteWish = viewModel::openEditorForNewWish,
            onEditWish = viewModel::openEditorForWish,
            onDeleteWish = viewModel::askToDeleteWish,
            onConfirmDelete = viewModel::confirmDeleteWish,
            onCancelDelete = viewModel::cancelDeleteWish
        )
    }
}

// ---------- piezas que usan las tres vistas ----------

//titulo de la vista con el boton de atras a la izquierda
@Composable
fun WishHeader(title: String, onBack: () -> Unit, textColor: Color = TextColor) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = ScreenTopPadding, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WishRoundButton(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Back", onClick = onBack)
        Text(text = title, style = MaterialTheme.typography.headlineLarge, color = textColor)
    }
}

//boton redondo de 48 dp (minimo tactil) con un icono
@Composable
fun WishRoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    backgroundColor: Color = SecondaryColor
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = backgroundColor)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = BackgroundLightColor)
    }
}

//aviso de que no hay internet; solo se dibuja si isOnline es false
@Composable
fun WishOfflineMessage(isOnline: Boolean, message: String) {
    if (isOnline) {
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundLightColor)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = Icons.Default.CloudOff, contentDescription = null, tint = TextColor)
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = TextColor)
    }
}
