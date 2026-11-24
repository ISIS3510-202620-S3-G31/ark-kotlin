package com.moviles.ark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moviles.ark.domain.models.CookieListItem
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.ErrorColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.CookieListTab

//listado de todas las galletas en un bottom sheet, con el mismo estilo de los de custom breathing
//dos pestanas: las del jarro (tocar abre el detalle) y las comidas (devolver al jarro o borrar para siempre)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookieListSheet(
    inJar: List<CookieListItem>,
    eaten: List<CookieListItem>,
    selectedTab: CookieListTab,
    onSelectTab: (CookieListTab) -> Unit,
    onCookieClick: (String) -> Unit,
    onReturnToJar: (String) -> Unit,
    onDeleteForever: (CookieListItem) -> Unit,
    onDismiss: () -> Unit,
    //el sheet es otra ventana y tapa los snackbars de la pantalla: se pintan tambien aqui
    snackbarHost: @Composable () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BackgroundColor,
        contentColor = TextColor
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            ) {
                Text(
                    text = "All cookies",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextColor,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.padding(top = 12.dp))
                ListTabs(
                    selectedTab = selectedTab,
                    inJarCount = inJar.size,
                    eatenCount = eaten.size,
                    onSelectTab = onSelectTab
                )
                Spacer(modifier = Modifier.padding(top = 12.dp))

                val items = if (selectedTab == CookieListTab.IN_JAR) inJar else eaten
                if (items.isEmpty()) {
                    Text(
                        text = if (selectedTab == CookieListTab.IN_JAR) "No cookies in the jar yet" else "You haven't eaten any cookie yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items, key = { it.id }) { item ->
                            CookieRow(
                                item = item,
                                isEaten = selectedTab == CookieListTab.EATEN,
                                onClick = { onCookieClick(item.id) },
                                onReturnToJar = { onReturnToJar(item.id) },
                                onDeleteForever = { onDeleteForever(item) }
                            )
                        }
                    }
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomCenter)) { snackbarHost() }
        }
    }
}

//dos pestanas tipo pildora con la paleta de la app
@Composable
private fun ListTabs(
    selectedTab: CookieListTab,
    inJarCount: Int,
    eatenCount: Int,
    onSelectTab: (CookieListTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(TextColor.copy(alpha = 0.06f))
            .padding(4.dp)
    ) {
        TabPill("In the jar ($inJarCount)", selectedTab == CookieListTab.IN_JAR, Modifier.weight(1f)) {
            onSelectTab(CookieListTab.IN_JAR)
        }
        TabPill("Eaten ($eatenCount)", selectedTab == CookieListTab.EATEN, Modifier.weight(1f)) {
            onSelectTab(CookieListTab.EATEN)
        }
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) SecondaryColor else BackgroundColor.copy(alpha = 0f))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = TextColor)
    }
}

//fila: titulo, 📷 si tiene imagen, ⏳ si falta subirla; en "Eaten" ademas devolver al jarro y borrar para siempre
@Composable
private fun CookieRow(
    item: CookieListItem,
    isEaten: Boolean,
    onClick: () -> Unit,
    onReturnToJar: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundLightColor)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            color = TextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (item.hasImage) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "📷", style = MaterialTheme.typography.bodyLarge)
        }
        //pendiente de subir a la nube; las sincronizadas no muestran nada
        if (!item.isSynced) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "⏳",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { contentDescription = "Waiting to upload" }
            )
        }
        if (isEaten) {
            IconButton(onClick = onReturnToJar) {
                Icon(Icons.Default.Replay, contentDescription = "Return to jar", tint = TextColor)
            }
            IconButton(onClick = onDeleteForever) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete forever", tint = ErrorColor)
            }
        } else {
            Spacer(modifier = Modifier.size(12.dp))
        }
    }
}

//confirmacion antes de borrar para siempre una galleta comida
@Composable
fun DeleteCookieDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundColor,
        titleContentColor = TextColor,
        textContentColor = TextColor,
        title = { Text("Delete forever?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                "\"$title\" will be deleted, picture included. This can't be undone.",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = ErrorColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextColor) }
        }
    )
}
