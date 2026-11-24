package com.moviles.ark.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

//vista 2: donde se escribe un deseo nuevo o se edita uno que ya existe
@Composable
fun WishEditorScreen(
    uiState: WishLanternUiState,
    onBack: () -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit
) {
    //el titulo y el boton cambian si se esta escribiendo uno nuevo o editando
    var title = "Write a wish"
    var saveButtonText = "Save and light the lantern"
    if (uiState.isEditing()) {
        title = "Edit wish"
        saveButtonText = "Save changes"
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                //sube el contenido cuando aparece el teclado
                .imePadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WishHeader(title = title, onBack = onBack)
            WishOfflineMessage(isOnline = uiState.isOnline, message = EDITOR_OFFLINE_MESSAGE)

            Spacer(modifier = Modifier.height(16.dp))
            //el farol apagado espera el deseo; se enciende al guardar
            LanternDrawing(isLit = false, modifier = Modifier.size(width = 80.dp, height = 100.dp))
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.editorText,
                onValueChange = onTextChange,
                placeholder = { Text("I wish...") },
                minLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BackgroundLightColor,
                    unfocusedContainerColor = BackgroundLightColor,
                    focusedBorderColor = PrimaryColor,
                    focusedTextColor = TextColor,
                    unfocusedTextColor = TextColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            //contador de letras, por ejemplo 12/150
            Text(
                text = "${uiState.editorText.length}/${WishModel.MAX_TEXT_LENGTH}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextColor.copy(alpha = 0.6f),
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            )

            val errorMessage = uiState.errorMessage
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentColor,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            //empuja el boton hasta abajo
            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onSave,
                enabled = uiState.canSave(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = TextColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(52.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = TextColor, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text(text = saveButtonText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WishEditorScreenPreview() {
    AppTheme {
        WishEditorScreen(
            uiState = WishLanternUiState(editorText = "To travel with my family", isOnline = false),
            onBack = {},
            onTextChange = {},
            onSave = {}
        )
    }
}
