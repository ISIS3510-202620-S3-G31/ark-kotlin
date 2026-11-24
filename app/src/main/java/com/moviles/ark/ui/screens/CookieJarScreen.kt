package com.moviles.ark.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.graphics.vector.ImageVector
import com.moviles.ark.ui.components.GiftFriendPickerDialog
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.viewmodels.CookieMessage
import com.moviles.ark.ui.viewmodels.CookieMessageAction
import com.moviles.ark.ui.viewmodels.CookieSocialViewModel
import kotlinx.coroutines.flow.Flow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.moviles.ark.domain.models.CookieMessages
import com.moviles.ark.domain.models.CookieModel
import com.moviles.ark.ui.components.CookieDetailDialog
import com.moviles.ark.ui.components.CookieJarDrawing
import com.moviles.ark.ui.components.CookieListSheet
import com.moviles.ark.ui.components.DeleteCookieDialog
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.ScreenTopPadding
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.CookieJarUiState
import com.moviles.ark.ui.viewmodels.CookieJarViewModel

//conecta la pantalla del jarro con su viewmodel; al salir decide si se pide feedback
//socialViewModel: amigos y regalos (solo 18+); lo comparten la pantalla de amigos y la bandeja
@Composable
fun CookieJarRoute(
    viewModel: CookieJarViewModel = viewModel(factory = CookieJarViewModel.Factory),
    socialViewModel: CookieSocialViewModel = viewModel(factory = CookieSocialViewModel.Factory),
    onBack: (wasCompleted: Boolean, durationSeconds: Int) -> Unit,
    onNewCookie: () -> Unit,
    onOpenFriends: () -> Unit = {},
    onOpenInbox: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val social by socialViewModel.uiState.collectAsStateWithLifecycle()
    //escuchar la bandeja mantiene vivo su listener (badge 🎁 en vivo) mientras se ve el jarro
    val inbox by socialViewModel.inbox.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    //avisos del jarro y de amigos y regalos en el mismo host; "View" abre la bandeja
    CookieMessagesEffect(viewModel.messageFlow, snackbarHostState) { action -> viewModel.onMessageAction(action) }
    CookieMessagesEffect(socialViewModel.messageFlow, snackbarHostState) { action ->
        when (action) {
            CookieMessageAction.ViewInbox -> onOpenInbox()
            is CookieMessageAction.Social -> socialViewModel.onMessageAction(action)
            else -> viewModel.onMessageAction(action)
        }
    }

    //latencia: se espera al siguiente cuadro; para entonces el primer dibujo de la pantalla ya termino
    LaunchedEffect(Unit) {
        withFrameNanos { }
        viewModel.onScreenRendered()
    }

    val leave = {
        val (completed, duration) = viewModel.finishSession()
        onBack(completed, duration)
    }
    BackHandler(onBack = leave)

    CookieJarScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = leave,
        onOpenList = viewModel::onOpenList,
        onNewCookie = onNewCookie,
        onCookieClick = { viewModel.onCookieClick(it.id) },
        social = CookieJarSocialBar(
            isVisible = social.isAdult,
            isOnline = social.isOnline,
            hasGifts = inbox.hasGifts,
            onOpenFriends = onOpenFriends,
            onOpenInbox = onOpenInbox,
        ),
    )

    if (uiState.showList) {
        CookieListSheet(
            inJar = uiState.inJarItems,
            eaten = uiState.eatenItems,
            selectedTab = uiState.listTab,
            onSelectTab = viewModel::onSelectTab,
            onCookieClick = viewModel::onCookieClick,
            onReturnToJar = viewModel::onReturnToJar,
            onDeleteForever = viewModel::onRequestDelete,
            onDismiss = viewModel::onDismissList,
            snackbarHost = { CookieSnackbarHost(snackbarHostState) }
        )
    }

    uiState.selectedCookie?.let { cookie ->
        CookieDetailDialog(
            cookie = cookie,
            image = uiState.selectedImage,
            isImageLoading = uiState.isImageLoading,
            imageNotice = uiState.imageNotice,
            onEat = { viewModel.onEatCookie(cookie.id) },
            onReturnToJar = { viewModel.onReturnToJar(cookie.id) },
            onDismiss = viewModel::onDismissDetail,
            //regalar solo 18+; sin red se ve deshabilitado y al tocarlo explica por que (M25)
            canGift = social.isAdult,
            isOnline = social.isOnline,
            onGift = { socialViewModel.onGiftClick(cookie.id) },
            snackbarHost = { CookieSnackbarHost(snackbarHostState) }
        )
    }

    if (social.giftCookieId != null) {
        GiftFriendPickerDialog(
            isLoading = social.isPickerLoading,
            needsActivation = social.pickerOverview != null && social.pickerOverview?.myProfile == null,
            friends = social.pickerFriends,
            sendingToUid = social.sendingToUid,
            isOnline = social.isOnline,
            onSend = socialViewModel::onSendGift,
            onOpenFriends = {
                socialViewModel.onDismissGiftPicker()
                viewModel.onDismissDetail()
                onOpenFriends()
            },
            onDismiss = socialViewModel::onDismissGiftPicker,
            snackbarHost = { CookieSnackbarHost(snackbarHostState) }
        )
    }

    uiState.pendingDelete?.let { item ->
        DeleteCookieDialog(
            title = item.title,
            onConfirm = viewModel::onConfirmDelete,
            onDismiss = viewModel::onDismissDelete
        )
    }
}

//textos de los avisos de red (M1 y M2)
private val NETWORK_STATUS_TEXTS = setOf(CookieMessages.WENT_OFFLINE, CookieMessages.BACK_ONLINE)

//muestra los avisos de una sola vez de un viewmodel; un mensaje igual al que ya se esta viendo no se repite
@Composable
fun CookieMessagesEffect(
    messages: Flow<CookieMessage>,
    hostState: SnackbarHostState,
    onAction: (CookieMessageAction) -> Unit,
) {
    LaunchedEffect(messages) {
        messages.collect { message ->
            val current = hostState.currentSnackbarData
            if (current?.visuals?.message == message.text) return@collect
            //"offline" y "back online" no se hacen fila: el estado nuevo de la red reemplaza al viejo
            if (message.isNetworkStatus && current?.visuals?.message in NETWORK_STATUS_TEXTS) current?.dismiss()
            //cada aviso en su corrutina: el host los muestra en fila y el collect sigue escuchando
            launch {
                val result = hostState.showSnackbar(
                    message = message.text,
                    actionLabel = message.actionLabel,
                    duration = if (message.isLong) SnackbarDuration.Long else SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) message.action?.let(onAction)
            }
        }
    }
}

//botones de amigos y bandeja arriba del jarro (solo 18+)
data class CookieJarSocialBar(
    val isVisible: Boolean = false,
    val isOnline: Boolean = true,
    //badge 🎁 en vivo: hay regalos esperando
    val hasGifts: Boolean = false,
    val onOpenFriends: () -> Unit = {},
    val onOpenInbox: () -> Unit = {},
)

//jarro con las galletas del dia, boton del listado encima y "+" para una galleta nueva
@Composable
fun CookieJarScreen(
    uiState: CookieJarUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenList: () -> Unit,
    onNewCookie: () -> Unit,
    onCookieClick: (CookieModel) -> Unit,
    social: CookieJarSocialBar = CookieJarSocialBar(),
) {
    Scaffold(
        containerColor = BackgroundColor,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { CookieSnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CookieTopBar(title = "Cookie Jar", onBack = onBack) {
                if (social.isVisible) {
                    //la bandeja se abre aun sin red: alli se explica que se necesita conexion (M29)
                    SocialIconButton(
                        icon = Icons.Default.CardGiftcard,
                        description = if (social.hasGifts) "Gifts waiting" else "Gifts",
                        isOnline = social.isOnline,
                        showBadge = social.hasGifts,
                        onClick = social.onOpenInbox
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SocialIconButton(
                        icon = Icons.Default.People,
                        description = "Friends",
                        isOnline = social.isOnline,
                        showBadge = false,
                        onClick = social.onOpenFriends
                    )
                }
            }

            //boton sobre el jarro: abre el listado de todas las galletas
            PillButton(
                text = "All cookies (${uiState.listItems.size})",
                color = SecondaryColor,
                onClick = onOpenList,
                leading = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = TextColor) },
                modifier = Modifier.padding(top = 8.dp)
            )

            //galletas guardadas solo en el telefono: se ve todo el tiempo, no solo mientras dura un snackbar
            uiState.pendingLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextColor.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CookieJarDrawing(
                    cookies = uiState.jarCookies,
                    onCookieClick = onCookieClick,
                    modifier = Modifier.widthIn(max = 360.dp)
                )
                if (uiState.isJarEmpty) {
                    Text(
                        text = "Your jar is empty.\nAdd your first cookie: a memory or a win you want to keep.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextColor.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .widthIn(max = 220.dp)
                            .padding(top = 48.dp)
                    )
                }
            }

            PillButton(
                text = "New cookie",
                color = PrimaryColor,
                onClick = onNewCookie,
                leading = { Icon(Icons.Default.Add, contentDescription = null, tint = TextColor) },
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}

//snackbars con los colores de la paleta: fondo oscuro del texto, letra clara y la accion en primary
@Composable
fun CookieSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState = hostState) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = TextColor,
            contentColor = BackgroundLightColor,
            actionColor = PrimaryColor,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

//boton atras redondo secondary y titulo en H1, como en custom breathing; actions va a la derecha
@Composable
fun CookieTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = ScreenTopPadding, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SecondaryColor)
                .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = TextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

//boton redondo de amigos o bandeja; sin red se ve apagado con wifi tachado, pero se puede tocar para saber por que
@Composable
fun SocialIconButton(
    icon: ImageVector,
    description: String,
    isOnline: Boolean,
    showBadge: Boolean,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.size(48.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(SecondaryColor.copy(alpha = if (isOnline) 1f else 0.4f))
                .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = description, tint = TextColor, modifier = Modifier.size(24.dp))
        }
        if (!isOnline) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = "No internet",
                tint = TextColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(BackgroundLightColor)
                    .padding(2.dp)
            )
        } else if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(AccentColor)
            )
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        leading()
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = TextColor)
    }
}

@Preview(showBackground = true)
@Composable
private fun CookieJarScreenPreview() {
    AppTheme {
        CookieJarScreen(
            uiState = CookieJarUiState(
                isLoading = false,
                jarCookies = (1..7).map { CookieModel(id = "$it", title = "Cookie $it", text = "") }
            ),
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onOpenList = {}, onNewCookie = {}, onCookieClick = {}
        )
    }
}
