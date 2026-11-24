package com.moviles.ark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moviles.ark.domain.models.WishModel
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.WishLanternUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//vista 3: todos los deseos del usuario; aqui se editan y se borran
@Composable
fun WishListScreen(
    uiState: WishLanternUiState,
    onBack: () -> Unit,
    onWriteWish: () -> Unit,
    onEditWish: (WishModel) -> Unit,
    onDeleteWish: (WishModel) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            WishHeader(title = "All my wishes", onBack = onBack)
            WishOfflineMessage(isOnline = uiState.isOnline, message = WISHES_OFFLINE_MESSAGE)

            val errorMessage = uiState.errorMessage
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentColor,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }

            if (uiState.isLoading) {
                LoadingWishes()
            } else if (uiState.wishes.isEmpty()) {
                EmptyWishes(onWriteWish = onWriteWish)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.wishes, key = { wish -> wish.id }) { wish ->
                        WishRow(
                            wish = wish,
                            onEdit = { onEditWish(wish) },
                            onDelete = { onDeleteWish(wish) }
                        )
                    }
                }
            }
        }
    }

    //confirmacion antes de borrar
    val wishToDelete = uiState.wishToDelete
    if (wishToDelete != null) {
        DeleteWishDialog(wish = wishToDelete, onConfirm = onConfirmDelete, onCancel = onCancelDelete)
    }
}

//una fila de la lista: el deseo, su fecha y los botones de editar y borrar
@Composable
private fun WishRow(wish: WishModel, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundLightColor)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LanternDrawing(isLit = true, modifier = Modifier.size(width = 28.dp, height = 36.dp))
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = wish.text, style = MaterialTheme.typography.bodyLarge, color = TextColor)
            Text(
                text = formatWishDate(wish.createdAt),
                style = MaterialTheme.typography.bodyMedium,
                color = TextColor.copy(alpha = 0.6f)
            )
        }
        IconButton(onClick = onEdit) {
            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit wish", tint = TextColor)
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete wish", tint = AccentColor)
        }
    }
}

//mientras llegan los deseos
@Composable
private fun LoadingWishes() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = PrimaryColor)
    }
}

//cuando todavia no hay deseos
@Composable
private fun EmptyWishes(onWriteWish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LanternDrawing(isLit = false, modifier = Modifier.size(width = 80.dp, height = 100.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "You haven't written any wishes yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onWriteWish,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = TextColor)
        ) {
            Text(text = "Write a wish", style = MaterialTheme.typography.labelLarge)
        }
    }
}

//pregunta si de verdad quiere borrar el deseo
@Composable
private fun DeleteWishDialog(wish: WishModel, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = BackgroundLightColor,
        title = { Text("Delete this wish?", color = TextColor) },
        text = { Text("\"${wish.text}\" will be deleted and its lantern will disappear from the sky.", color = TextColor) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = AccentColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = TextColor)
            }
        }
    )
}

//fecha de un deseo, por ejemplo "Nov 24, 2026"
fun formatWishDate(millis: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(millis))
}

@Preview(showBackground = true)
@Composable
fun WishListScreenPreview() {
    val wishes = listOf(
        WishModel("1", "To travel with my family", 0L, 0L),
        WishModel("2", "To finish the semester calm", 0L, 0L)
    )
    AppTheme {
        WishListScreen(
            uiState = WishLanternUiState(wishes = wishes, isLoading = false),
            onBack = {},
            onWriteWish = {},
            onEditWish = {},
            onDeleteWish = {},
            onConfirmDelete = {},
            onCancelDelete = {}
        )
    }
}
