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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moviles.ark.domain.models.CookieMessages
import com.moviles.ark.domain.models.SharedCookie
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.CookieMessageAction
import com.moviles.ark.ui.viewmodels.CookieSocialViewModel
import com.moviles.ark.ui.viewmodels.InboxStatus
import com.moviles.ark.ui.viewmodels.InboxUiState

//bandeja de regalos: vive en el backend, asi que sin red no muestra nada (M29)
//usa el mismo viewmodel social del jarro (su listener sigue vivo al pasar de una pantalla a la otra)
@Composable
fun CookieInboxRoute(socialViewModel: CookieSocialViewModel, onBack: () -> Unit) {
    val social by socialViewModel.uiState.collectAsStateWithLifecycle()
    val inbox by socialViewModel.inbox.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CookieMessagesEffect(socialViewModel.messageFlow, snackbarHostState) { action ->
        if (action is CookieMessageAction.Social) socialViewModel.onMessageAction(action)
    }
    //mientras esta abierta se borran los vencidos y se avisa si uno vence (M33)
    DisposableEffect(Unit) {
        socialViewModel.onInboxOpened()
        onDispose { socialViewModel.onInboxClosed() }
    }

    CookieInboxScreen(
        inbox = inbox,
        isOnline = social.isOnline,
        acceptingIds = social.acceptingIds,
        decliningIds = social.decliningIds,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAccept = socialViewModel::onAcceptGift,
        onDecline = socialViewModel::onDeclineGift,
    )
}

@Composable
fun CookieInboxScreen(
    inbox: InboxUiState,
    isOnline: Boolean,
    acceptingIds: Set<String>,
    decliningIds: Set<String>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAccept: (SharedCookie) -> Unit,
    onDecline: (SharedCookie) -> Unit,
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
                .navigationBarsPadding()
        ) {
            CookieTopBar(title = "Gifts", onBack = onBack)
            when (inbox.status) {
                InboxStatus.LOADING, InboxStatus.HIDDEN -> CenterBox {
                    CircularProgressIndicator(color = SecondaryColor, modifier = Modifier.size(32.dp))
                }
                InboxStatus.OFFLINE -> CenterBox {
                    Icon(Icons.Default.WifiOff, contentDescription = null, tint = TextColor, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    CenterText(CookieMessages.GIFTS_NEED_CONNECTION)
                }
                InboxStatus.READY -> if (inbox.gifts.isEmpty()) {
                    CenterBox { CenterText("No gifts right now.\nWhen a friend sends you a cookie, it shows up here.") }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(inbox.gifts, key = { it.shareId }) { gift ->
                            GiftCard(
                                gift = gift,
                                now = inbox.now,
                                isOnline = isOnline,
                                isAccepting = gift.shareId in acceptingIds,
                                isDeclining = gift.shareId in decliningIds,
                                onAccept = { onAccept(gift) },
                                onDecline = { onDecline(gift) },
                            )
                        }
                    }
                }
            }
        }
    }
}

//un regalo: de quien, titulo y texto (la foto se baja recien al aceptar)
@Composable
private fun GiftCard(
    gift: SharedCookie,
    now: Long,
    isOnline: Boolean,
    isAccepting: Boolean,
    isDeclining: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BackgroundLightColor)
            .padding(16.dp)
    ) {
        Text(text = "🎁 from @${gift.fromUsername}", style = MaterialTheme.typography.bodyMedium, color = TextColor.copy(alpha = 0.8f))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = gift.title, style = MaterialTheme.typography.titleLarge, color = TextColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = gift.text, style = MaterialTheme.typography.bodyLarge, color = TextColor)
        Spacer(modifier = Modifier.height(8.dp))
        val details = buildList {
            if (gift.hasImage) add("📷 includes a picture")
            val days = gift.daysLeft(now)
            add(if (days == 1) "Expires in 1 day" else "Expires in $days days")
        }
        Text(text = details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = TextColor.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SocialPill(
                text = "Accept",
                color = PrimaryColor,
                isOnline = isOnline,
                isBusy = isAccepting,
                enabled = !isAccepting && !isDeclining,
                onClick = onAccept,
                modifier = Modifier.weight(1f)
            )
            SocialPill(
                text = "Decline",
                color = SecondaryColor,
                isOnline = isOnline,
                isBusy = isDeclining,
                enabled = !isAccepting && !isDeclining,
                onClick = onDecline,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

//boton social: sin red se ve apagado con wifi tachado pero se puede tocar (el snackbar explica por que)
@Composable
fun SocialPill(
    text: String,
    color: Color,
    isOnline: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(color.copy(alpha = if (isOnline && enabled) 1f else 0.4f))
            .clickable(enabled = enabled && !isBusy, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        when {
            isBusy -> CircularProgressIndicator(color = TextColor, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else -> {
                if (!isOnline) {
                    Icon(Icons.Default.WifiOff, contentDescription = "No internet", tint = TextColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(text = text, style = MaterialTheme.typography.labelLarge, color = TextColor)
            }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun CenterText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = TextColor.copy(alpha = 0.8f),
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 280.dp)
    )
}
