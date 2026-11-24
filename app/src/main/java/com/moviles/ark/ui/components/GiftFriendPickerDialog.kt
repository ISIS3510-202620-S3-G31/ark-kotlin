package com.moviles.ark.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.moviles.ark.domain.models.Friend
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor

//elegir a que amigo regalar la galleta (uno solo); la galleta propia no cambia, el amigo recibe una copia
@Composable
fun GiftFriendPickerDialog(
    isLoading: Boolean,
    //no ha activado amigos: no tiene username para firmar el regalo
    needsActivation: Boolean,
    friends: List<Friend>,
    //amigo al que se esta enviando (spinner y los demas botones apagados)
    sendingToUid: String?,
    isOnline: Boolean,
    onSend: (Friend) -> Unit,
    onOpenFriends: () -> Unit,
    onDismiss: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(BackgroundColor)
                    .padding(20.dp)
            ) {
                Text(text = "Gift this cookie", style = MaterialTheme.typography.headlineSmall, color = TextColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose one friend. They get a copy; yours stays in your jar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextColor.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (isLoading) {
                    PickerLoading()
                } else if (needsActivation || friends.isEmpty()) {
                    //sin username o sin amigos no hay a quien regalar: se lleva a la pantalla de amigos
                    NoOneToGift(needsActivation = needsActivation, onOpenFriends = onOpenFriends)
                } else {
                    FriendsToGiftList(friends = friends, sendingToUid = sendingToUid, isOnline = isOnline, onSend = onSend)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, enabled = sendingToUid == null, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", style = MaterialTheme.typography.labelLarge, color = TextColor)
                }
            }
            //el dialogo es otra ventana y tapa los snackbars de la pantalla: se pintan tambien aqui
            Box(modifier = Modifier.align(Alignment.BottomCenter)) { snackbarHost() }
        }
    }
}

@Composable
private fun PickerLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = SecondaryColor, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun NoOneToGift(needsActivation: Boolean, onOpenFriends: () -> Unit) {
    val message = if (needsActivation) "Turn on friends to send gifts." else "You have no friends yet. Add one by their username."
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = TextColor,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    )
    Button(
        onClick = onOpenFriends,
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = TextColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text("Go to friends", style = MaterialTheme.typography.labelLarge)
    }
}

//mientras se envia a uno, los demas se apagan
@Composable
private fun FriendsToGiftList(friends: List<Friend>, sendingToUid: String?, isOnline: Boolean, onSend: (Friend) -> Unit) {
    LazyColumn(
        modifier = Modifier.heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(friends, key = { it.uid }) { friend ->
            FriendGiftRow(
                friend = friend,
                isSending = sendingToUid == friend.uid,
                enabled = sendingToUid == null,
                isOnline = isOnline,
                onClick = { onSend(friend) }
            )
        }
    }
}

//un amigo: avatar, @username y "Send" (o el spinner mientras se envia)
@Composable
private fun FriendGiftRow(friend: Friend, isSending: Boolean, enabled: Boolean, isOnline: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundLightColor)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Send to @${friend.username}", onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(avatarDrawable(friend.avatarId)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "@${friend.username}",
            style = MaterialTheme.typography.bodyLarge,
            color = TextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isSending) {
            CircularProgressIndicator(color = SecondaryColor, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            //apagado sin red o mientras se envia a otro
            val sendAlpha = if (enabled && isOnline) 1f else 0.4f
            Text(
                text = "Send",
                style = MaterialTheme.typography.labelLarge,
                color = TextColor.copy(alpha = sendAlpha)
            )
        }
    }
}
