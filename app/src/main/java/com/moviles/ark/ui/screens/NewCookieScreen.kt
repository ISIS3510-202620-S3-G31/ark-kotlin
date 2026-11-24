package com.moviles.ark.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.domain.models.CookieMessages
import com.moviles.ark.domain.models.CookieModel
import com.moviles.ark.ui.components.ModernVoiceInputBox
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.ErrorColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.NewCookieEvent
import com.moviles.ark.ui.viewmodels.NewCookieUiState
import com.moviles.ark.ui.viewmodels.NewCookieViewModel

//conecta el formulario con su viewmodel; al guardar devuelve la galleta para que el jarro muestre el aviso
@Composable
fun NewCookieRoute(
    viewModel: NewCookieViewModel = viewModel(factory = NewCookieViewModel.Factory),
    onBack: () -> Unit,
    onSaved: (typedTitle: String, cookie: CookieModel) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is NewCookieEvent.Saved -> onSaved(event.typedTitle, event.cookie)
                is NewCookieEvent.Message -> snackbarHostState.showSnackbar(event.text, duration = SnackbarDuration.Long)
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onCaptureResult(success)
    }
    //photo picker del sistema: no pide permisos de galeria
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onImageSelected(uri.toString())
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.prepareCapture()?.let { takePictureLauncher.launch(Uri.parse(it)) }
    }

    NewCookieScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onTitleChange = viewModel::onTitleChange,
        onTextChange = viewModel::onTextChange,
        onTakePhoto = {
            if (viewModel.hasCameraPermission()) {
                viewModel.prepareCapture()?.let { takePictureLauncher.launch(Uri.parse(it)) }
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        onPickImage = { pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemoveImage = viewModel::onRemoveImage,
        onSave = viewModel::onSave
    )
}

//titulo (60), texto (280) con dictado, imagen opcional y el titulo final si ya existe uno igual
@Composable
fun NewCookieScreen(
    uiState: NewCookieUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onSave: () -> Unit,
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
                .imePadding()
        ) {
            CookieTopBar(title = "New cookie", onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                FieldLabel("Title")
                ModernVoiceInputBox(
                    value = uiState.title,
                    onValueChange = onTitleChange,
                    maxLength = CookieModel.MAX_TITLE_LENGTH,
                    placeholder = "My graduation day",
                    minHeight = 24.dp,
                    enabled = !uiState.isSaving
                )
                uiState.titleError?.let { ErrorText(it) }
                //antes de guardar se dice con que titulo quedara si ya existe uno igual
                uiState.finalTitle?.let { finalTitle ->
                    Text(
                        text = CookieMessages.willBeSavedAs(finalTitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextColor.copy(alpha = 0.75f),
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                FieldLabel("What do you want to remember?")
                ModernVoiceInputBox(
                    value = uiState.text,
                    onValueChange = onTextChange,
                    maxLength = CookieModel.MAX_TEXT_LENGTH,
                    placeholder = "Write it or dictate it…",
                    minHeight = 120.dp,
                    enabled = !uiState.isSaving
                )
                uiState.textError?.let { ErrorText(it) }

                Spacer(modifier = Modifier.height(20.dp))
                FieldLabel("Picture (optional)")
                if (uiState.imageUri != null) {
                    ImagePreview(uiState = uiState, onRemove = onRemoveImage)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        ImageSourceButton("Camera", Icons.Default.PhotoCamera, onTakePhoto, Modifier.weight(1f))
                        ImageSourceButton("Gallery", Icons.Default.PhotoLibrary, onPickImage, Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Button(
                onClick = onSave,
                enabled = !uiState.isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryColor,
                    contentColor = TextColor,
                    disabledContainerColor = PrimaryColor.copy(alpha = 0.5f),
                    disabledContentColor = TextColor.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 8.dp)
                    .height(52.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = TextColor, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text("Save cookie 🍪", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = TextColor.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = ErrorColor,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun ImageSourceButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SecondaryColor.copy(alpha = 0.35f))
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = TextColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = TextColor)
    }
}

//vista previa de la imagen elegida con una x para quitarla
@Composable
private fun ImagePreview(uiState: NewCookieUiState, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(TextColor.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        val preview = uiState.imagePreview
        if (preview != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = "Picture for the cookie",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        } else {
            CircularProgressIndicator(color = SecondaryColor, modifier = Modifier.size(32.dp))
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(BackgroundColor.copy(alpha = 0.9f))
                .clickable(role = Role.Button, onClickLabel = "Remove picture", enabled = !uiState.isSaving, onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove picture", tint = TextColor, modifier = Modifier.size(20.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NewCookieScreenPreview() {
    AppTheme {
        NewCookieScreen(
            uiState = NewCookieUiState(title = "Mi grado", finalTitle = "Mi grado-1"),
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onTitleChange = {}, onTextChange = {}, onTakePhoto = {}, onPickImage = {},
            onRemoveImage = {}, onSave = {}
        )
    }
}
