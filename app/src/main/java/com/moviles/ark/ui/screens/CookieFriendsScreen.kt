package com.moviles.ark.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moviles.ark.domain.models.CookieMessages
import com.moviles.ark.domain.models.FoundUser
import com.moviles.ark.domain.models.FriendRelation
import com.moviles.ark.domain.models.SentGift
import com.moviles.ark.ui.components.avatarDrawable
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.CookieMessageAction
import com.moviles.ark.ui.viewmodels.CookieSocialUiState
import com.moviles.ark.ui.viewmodels.CookieSocialViewModel
import com.moviles.ark.ui.viewmodels.SocialConfirm
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//amigos del cookie jar (solo 18+): activar amigos con un username, buscar, solicitudes, amigos, regalos enviados y bloqueados
//todo menos "Sent gifts" (room) viene del servidor; sin red los botones se ven apagados y al tocarlos dicen por que (M18)
@Composable
fun CookieFriendsRoute(socialViewModel: CookieSocialViewModel, onBack: () -> Unit) {
    val uiState by socialViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CookieMessagesEffect(socialViewModel.messageFlow, snackbarHostState) { action ->
        if (action is CookieMessageAction.Social) socialViewModel.onMessageAction(action)
    }
    DisposableEffect(Unit) {
        socialViewModel.onFriendsOpened()
        onDispose { socialViewModel.onFriendsClosed() }
    }

    CookieFriendsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        actions = FriendsActions(
            onUsernameChange = socialViewModel::onUsernameChange,
            onActivate = socialViewModel::onActivateFriends,
            onDeactivate = socialViewModel::onRequestDeactivate,
            onSearchChange = socialViewModel::onSearchChange,
            onSearch = socialViewModel::onSearch,
            onSearchResultAction = socialViewModel::onSearchResultAction,
            onAcceptRequest = socialViewModel::onAcceptRequest,
            onDeclineRequest = socialViewModel::onDeclineRequest,
            onRemoveFriend = socialViewModel::onRequestRemoveFriend,
            onBlock = socialViewModel::onRequestBlock,
            onUnblock = socialViewModel::onUnblock,
            onCancelGift = socialViewModel::onCancelGift,
        )
    )

    uiState.confirm?.let { confirm ->
        SocialConfirmDialog(confirm = confirm, onConfirm = socialViewModel::onConfirm, onDismiss = socialViewModel::onDismissConfirm)
    }
}

//lo que puede hacer el usuario en la pantalla de amigos
data class FriendsActions(
    val onUsernameChange: (String) -> Unit = {},
    val onActivate: () -> Unit = {},
    val onDeactivate: () -> Unit = {},
    val onSearchChange: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onSearchResultAction: (FoundUser) -> Unit = {},
    val onAcceptRequest: (String) -> Unit = {},
    val onDeclineRequest: (String) -> Unit = {},
    val onRemoveFriend: (com.moviles.ark.domain.models.Friend) -> Unit = {},
    val onBlock: (uid: String, username: String) -> Unit = { _, _ -> },
    val onUnblock: (String) -> Unit = {},
    val onCancelGift: (String) -> Unit = {},
)

@Composable
fun CookieFriendsScreen(
    uiState: CookieSocialUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    actions: FriendsActions,
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
            CookieTopBar(title = "Friends", onBack = onBack)
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                profileSection(uiState, actions)
                if (uiState.isFriendsOn) {
                    searchSection(uiState, actions)
                    requestsSection(uiState, actions)
                    friendsSection(uiState, actions)
                }
                sentGiftsSection(uiState, actions)
                if (uiState.isFriendsOn) blockedSection(uiState, actions)
            }
        }
    }
}

private fun LazyListScope.profileSection(uiState: CookieSocialUiState, actions: FriendsActions) {
    val profile = uiState.overview?.myProfile
    item(key = "profile") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BackgroundLightColor)
                .padding(16.dp)
        ) {
            when {
                profile != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarCircle(profile.avatarId, 48)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("@${profile.username}", style = MaterialTheme.typography.titleLarge, color = TextColor)
                            Text(
                                "Others only see your username and avatar.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = actions.onDeactivate, modifier = Modifier.align(Alignment.End)) {
                        Text("Turn off friends", style = MaterialTheme.typography.labelLarge, color = TextColor)
                    }
                }
                uiState.overview != null -> ActivateFriends(uiState, actions)
                uiState.isLoadingFriends || uiState.isOnline -> Box(
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SecondaryColor, modifier = Modifier.size(32.dp))
                }
                //sin red no se sabe si ya activo amigos: no se ofrece activarlos otra vez
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiOff, contentDescription = null, tint = TextColor, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(CookieMessages.FRIENDS_NEED_INTERNET, style = MaterialTheme.typography.bodyLarge, color = TextColor)
                }
            }
        }
    }
}

//"Activar amigos": explica que ve el otro (solo username y avatar) antes de crear el perfil publico
@Composable
private fun ActivateFriends(uiState: CookieSocialUiState, actions: FriendsActions) {
    Text("Turn on friends", style = MaterialTheme.typography.titleLarge, color = TextColor)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "Pick a username so friends can find you and you can send each other cookies.\n" +
            "• Friends find you only by your exact username: there is no list of users.\n" +
            "• Others only see your username and your avatar. Never your name, email, birth date or photos.\n" +
            "• You can turn friends off anytime: your username and public profile are deleted.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextColor
    )
    Spacer(modifier = Modifier.height(12.dp))
    SocialTextField(
        value = uiState.usernameInput,
        onValueChange = actions.onUsernameChange,
        label = "Username (3–20: a–z, 0–9, _ .)",
        enabled = !uiState.isActivating,
        onDone = actions.onActivate,
    )
    uiState.usernameError?.let {
        Spacer(modifier = Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    Spacer(modifier = Modifier.height(12.dp))
    SocialPill(
        text = "Turn on friends",
        color = PrimaryColor,
        isOnline = uiState.isOnline,
        isBusy = uiState.isActivating,
        onClick = actions.onActivate,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun LazyListScope.searchSection(uiState: CookieSocialUiState, actions: FriendsActions) {
    item(key = "search_title") { SectionTitle("Find a friend") }
    item(key = "search") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                SocialTextField(
                    value = uiState.searchInput,
                    onValueChange = actions.onSearchChange,
                    label = "Exact username",
                    enabled = !uiState.isSearching,
                    onDone = actions.onSearch,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            SocialPill(text = "Search", color = SecondaryColor, isOnline = uiState.isOnline, isBusy = uiState.isSearching, onClick = actions.onSearch)
        }
    }
    uiState.searchResult?.let { found ->
        item(key = "search_result") {
            val (label, enabled) = when (found.relation) {
                FriendRelation.NONE -> "Add friend" to true
                FriendRelation.REQUESTED -> "Requested" to false
                FriendRelation.WANTS_TO_BE_FRIENDS -> "Accept" to true
                FriendRelation.FRIENDS -> "Friends ✓" to false
                FriendRelation.BLOCKED_BY_ME -> "Unblock" to true
                FriendRelation.YOU -> "That's you" to false
            }
            PersonRow(avatarId = found.profile.avatarId, username = found.profile.username) {
                SocialPill(
                    text = label,
                    color = PrimaryColor,
                    isOnline = uiState.isOnline,
                    isBusy = found.profile.uid in uiState.busyIds,
                    enabled = enabled,
                    onClick = { actions.onSearchResultAction(found) }
                )
            }
        }
    }
}

private fun LazyListScope.requestsSection(uiState: CookieSocialUiState, actions: FriendsActions) {
    val requests = uiState.overview?.requests.orEmpty()
    if (requests.isEmpty()) return
    item(key = "requests_title") { SectionTitle("Friend requests") }
    items(requests, key = { "request_${it.fromUid}" }) { request ->
        val busy = request.fromUid in uiState.busyIds
        Column {
            PersonRow(avatarId = null, username = request.fromUsername) {
                SocialPill("Accept", PrimaryColor, uiState.isOnline, onClick = { actions.onAcceptRequest(request.fromUid) }, isBusy = busy)
                Spacer(modifier = Modifier.width(6.dp))
                SocialPill("Decline", SecondaryColor, uiState.isOnline, onClick = { actions.onDeclineRequest(request.fromUid) }, enabled = !busy)
            }
            TextButton(onClick = { actions.onBlock(request.fromUid, request.fromUsername) }) {
                Text("Block @${request.fromUsername}", style = MaterialTheme.typography.labelLarge, color = AccentColor)
            }
        }
    }
}

private fun LazyListScope.friendsSection(uiState: CookieSocialUiState, actions: FriendsActions) {
    val friends = uiState.overview?.friends.orEmpty()
    item(key = "friends_title") { SectionTitle("Friends (${friends.size})") }
    if (friends.isEmpty()) {
        item(key = "friends_empty") { HintText("No friends yet. Search for someone by their exact username.") }
    }
    items(friends, key = { "friend_${it.uid}" }) { friend ->
        val busy = friend.uid in uiState.busyIds
        PersonRow(avatarId = friend.avatarId, username = friend.username) {
            SocialPill("Remove", SecondaryColor, uiState.isOnline, onClick = { actions.onRemoveFriend(friend) }, isBusy = busy)
            Spacer(modifier = Modifier.width(6.dp))
            SocialPill("Block", AccentColor, uiState.isOnline, onClick = { actions.onBlock(friend.uid, friend.username) }, enabled = !busy)
        }
    }
}

//regalos enviados: solo en este telefono; siempre dicen "Sent ✓" (el remitente no sabe si se acepto o rechazo)
private fun LazyListScope.sentGiftsSection(uiState: CookieSocialUiState, actions: FriendsActions) {
    if (uiState.sentGifts.isEmpty()) return
    item(key = "sent_title") { SectionTitle("Sent gifts") }
    items(uiState.sentGifts, key = { "sent_${it.shareId}" }) { gift -> SentGiftRow(gift, uiState, actions) }
}

@Composable
private fun SentGiftRow(gift: SentGift, uiState: CookieSocialUiState, actions: FriendsActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundLightColor)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(gift.title, style = MaterialTheme.typography.bodyLarge, color = TextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "to @${gift.toUsername} · ${dayLabel(gift.sentAt)} · Sent ✓",
                style = MaterialTheme.typography.bodyMedium,
                color = TextColor.copy(alpha = 0.7f)
            )
        }
        if (gift.canCancel(System.currentTimeMillis())) {
            Spacer(modifier = Modifier.width(8.dp))
            SocialPill("Cancel", SecondaryColor, uiState.isOnline, onClick = { actions.onCancelGift(gift.shareId) }, isBusy = gift.shareId in uiState.busyIds)
        }
    }
}

private fun LazyListScope.blockedSection(uiState: CookieSocialUiState, actions: FriendsActions) {
    val blocked = uiState.overview?.blocked.orEmpty()
    if (blocked.isEmpty()) return
    item(key = "blocked_title") { SectionTitle("Blocked") }
    items(blocked, key = { "blocked_${it.uid}" }) { user ->
        PersonRow(avatarId = null, username = user.username) {
            SocialPill("Unblock", SecondaryColor, uiState.isOnline, onClick = { actions.onUnblock(user.uid) }, isBusy = user.uid in uiState.busyIds)
        }
    }
}

@Composable
private fun PersonRow(avatarId: String?, username: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundLightColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatarId != null) {
            AvatarCircle(avatarId, 36)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = "@$username",
            style = MaterialTheme.typography.bodyLarge,
            color = TextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
private fun AvatarCircle(avatarId: String, sizeDp: Int) {
    Image(
        painter = painterResource(avatarDrawable(avatarId)),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(sizeDp.dp).clip(CircleShape)
    )
}

@Composable
private fun SocialTextField(value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean, onDone: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        prefix = { Text("@", style = MaterialTheme.typography.bodyLarge, color = TextColor) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextColor),
        label = { Text(label, style = LocalTextStyle.current.copy(fontFamily = FigtreeFontFamily)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SecondaryColor,
            unfocusedBorderColor = TextColor.copy(alpha = 0.3f),
            focusedLabelColor = TextColor,
            unfocusedLabelColor = TextColor.copy(alpha = 0.7f),
            cursorColor = TextColor
        )
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleLarge, color = TextColor, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun HintText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = TextColor.copy(alpha = 0.7f))
}

//confirmacion antes de desactivar amigos, quitar un amigo o bloquear
@Composable
private fun SocialConfirmDialog(confirm: SocialConfirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (title, body, action) = when (confirm) {
        SocialConfirm.Deactivate -> Triple(
            "Turn off friends?",
            "Your username and public profile will be deleted. Friends won't find you or send you gifts. Your cookies stay in your jar.",
            "Turn off"
        )
        is SocialConfirm.RemoveFriend -> Triple("Remove @${confirm.username}?", "You won't be able to send each other gifts.", "Remove")
        is SocialConfirm.Block -> Triple(
            "Block @${confirm.username}?",
            "They won't find you, send you friend requests or gifts. They are not told.",
            "Block"
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundColor,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = TextColor) },
        text = { Text(body, style = MaterialTheme.typography.bodyLarge, color = TextColor) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(action, style = MaterialTheme.typography.labelLarge, color = AccentColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge, color = TextColor) }
        }
    )
}

private fun dayLabel(millis: Long): String = SimpleDateFormat("MMM d", Locale.US).format(Date(millis))
