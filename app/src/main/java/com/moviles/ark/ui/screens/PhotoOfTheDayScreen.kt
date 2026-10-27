package com.moviles.ark.ui.screens

import com.bumptech.glide.Glide
import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.data.local.sensors.CameraHelper
import com.moviles.ark.domain.models.PhotoDay
import com.moviles.ark.domain.models.PhotoDayState
import com.moviles.ark.domain.models.PhotoEntryModel
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.SuccessColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.PhotoOfTheDayUiState
import com.moviles.ark.ui.viewmodels.PhotoOfTheDayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val CardFill = TextColor.copy(alpha = 0.06f)

@Composable
fun PhotoOfTheDayScreen(
    uiState: PhotoOfTheDayUiState,
    onBack: () -> Unit,
    onTakePhotoClick: (() -> Unit)?,
    onPickPhotoClick: (() -> Unit)? = null,
    onSelectDay: (PhotoDay) -> Unit,
    onBackToToday: () -> Unit,
    onShowDeleteDialog: (Boolean) -> Unit,
    onConfirmDelete: () -> Unit,
    onRetakeClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Header(todayLabel = uiState.todayLabel, onBack = onBack)
            Spacer(modifier = Modifier.height(20.dp))
            WeekStrip(week = uiState.week, onSelectDay = onSelectDay)
            Spacer(modifier = Modifier.height(24.dp))

            val todayPhoto = uiState.todayPhoto
            val pendingPhotoUri = uiState.pendingPhotoUri

            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SecondaryColor)
                }
                uiState.isPastDaySelected -> PastDayPhotoSection(
                    photo = uiState.selectedDayPhoto,
                    dayName = uiState.selectedDayName ?: "Selected Day",
                    onBackToToday = onBackToToday
                )
                todayPhoto != null -> SavedPhotoSection(
                    photo = todayPhoto,
                    onRetakeClick = onTakePhotoClick,
                    onDeleteClick = { onShowDeleteDialog(true) }
                )
                pendingPhotoUri != null -> PendingPhotoSection(
                    uri = pendingPhotoUri,
                    isSaving = uiState.isSaving,
                    onRetakeClick = onRetakeClick,
                    onSaveClick = onSaveClick
                )
                else -> EmptyPhotoSection(
                    onTakePhotoClick = onTakePhotoClick,
                    onPickPhotoClick = onPickPhotoClick
                )
            }

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(uiState.errorMessage, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { onShowDeleteDialog(false) },
            title = {
                Text("Delete photo?", style = MaterialTheme.typography.titleLarge, color = TextColor)
            },
            text = {
                Text(
                    "Are you sure you want to delete today's photo? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextColor.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { onShowDeleteDialog(false) },
                    border = BorderStroke(1.dp, SecondaryColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            },
            containerColor = BackgroundColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun Header(todayLabel: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SecondaryColor)
                .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextColor, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text("Photo of the day", style = MaterialTheme.typography.headlineLarge, color = TextColor)
            Text(todayLabel, style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun WeekStrip(week: List<PhotoDay>, onSelectDay: (PhotoDay) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEach { day ->
            DayDot(day = day, onClick = { onSelectDay(day) })
        }
    }
}

@Composable
private fun DayDot(day: PhotoDay, onClick: () -> Unit) {
    val stateText = when (day.state) {
        PhotoDayState.TAKEN -> "photo taken"
        PhotoDayState.TODAY -> "today"
        PhotoDayState.MISSED -> "no photo"
        PhotoDayState.UPCOMING -> "not yet"
    }
    Column(
        modifier = Modifier
            .clickable(role = Role.Button, onClickLabel = "${day.name}, $stateText", onClick = onClick)
            .clearAndSetSemantics { contentDescription = "${day.name}, $stateText" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (day.state) {
            PhotoDayState.TAKEN -> Box(
                modifier = Modifier.size(36.dp).background(SuccessColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = TextColor, modifier = Modifier.size(16.dp))
            }
            PhotoDayState.TODAY -> Box(
                modifier = Modifier.size(36.dp).border(2.dp, PrimaryColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(10.dp).background(PrimaryColor, CircleShape))
            }
            PhotoDayState.MISSED -> Box(modifier = Modifier.size(36.dp).background(TextColor.copy(alpha = 0.1f), CircleShape))
            PhotoDayState.UPCOMING -> Box(modifier = Modifier.size(36.dp).border(1.dp, TextColor.copy(alpha = 0.2f), CircleShape))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            day.shortLabel,
            style = MaterialTheme.typography.labelLarge,
            color = if (day.state == PhotoDayState.TODAY) TextColor else TextColor.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun EmptyPhotoSection(
    onTakePhotoClick: (() -> Unit)?,
    onPickPhotoClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .background(CardFill, RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = TextColor.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("No photo yet today", style = MaterialTheme.typography.headlineSmall, color = TextColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "One photo a day, one new memory.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextColor.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
    Spacer(modifier = Modifier.height(20.dp))
    Button(
        onClick = { onTakePhotoClick?.invoke() },
        enabled = onTakePhotoClick != null,
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Take photo with camera", style = MaterialTheme.typography.labelLarge)
    }
    if (onPickPhotoClick != null) {
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = { onPickPhotoClick() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            border = BorderStroke(1.dp, SecondaryColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)
        ) {
            Icon(Icons.Filled.Image, contentDescription = null, tint = TextColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Choose from gallery", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PendingPhotoSection(
    uri: String,
    isSaving: Boolean,
    onRetakeClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    PhotoThumbnail(path = uri, modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f))
    Spacer(modifier = Modifier.height(16.dp))
    if (isSaving) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SecondaryColor)
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onRetakeClick,
                enabled = !isSaving,
                modifier = Modifier.weight(1f).height(50.dp),
                border = BorderStroke(1.dp, SecondaryColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)
            ) {
                Text("Retake", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onSaveClick,
                enabled = !isSaving,
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SavedPhotoSection(
    photo: PhotoEntryModel,
    onRetakeClick: (() -> Unit)?,
    onDeleteClick: () -> Unit
) {
    PhotoThumbnail(path = photo.localFilePath, modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f))
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .background(SuccessColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = TextColor, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Saved for today", style = MaterialTheme.typography.labelLarge, color = TextColor)
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { onRetakeClick?.invoke() },
            enabled = onRetakeClick != null,
            modifier = Modifier.weight(1f).height(50.dp)
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retake photo", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedButton(
            onClick = onDeleteClick,
            modifier = Modifier.weight(1f).height(50.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PastDayPhotoSection(
    photo: PhotoEntryModel?,
    dayName: String,
    onBackToToday: () -> Unit
) {
    if (photo != null) {
        PhotoThumbnail(path = photo.localFilePath, modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f))
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .background(CardFill, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Past memory - $dayName", style = MaterialTheme.typography.labelLarge, color = TextColor)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Entries from past days are in read-only mode.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextColor.copy(alpha = 0.7f)
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(CardFill, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.BrokenImage, contentDescription = null, tint = TextColor.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("No photo on $dayName", style = MaterialTheme.typography.headlineSmall, color = TextColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text("No photo was captured on this day.", style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.7f))
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedButton(
        onClick = onBackToToday,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        border = BorderStroke(1.dp, SecondaryColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)
    ) {
        Text("Back to today", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PhotoThumbnail(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val result by produceState<Result<ImageBitmap>?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { runCatching { decodeThumbnail(context, path) } }
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).background(CardFill),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = result?.getOrNull()
        when {
            bitmap != null -> Image(bitmap, contentDescription = "Photo of the day", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            result == null -> CircularProgressIndicator(color = SecondaryColor)
            else -> Icon(Icons.Filled.BrokenImage, contentDescription = "The photo could not be opened", tint = TextColor.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
        }
    }
}

private fun decodeThumbnail(context: Context, path: String): ImageBitmap {
    val model: Any = if (path.startsWith("content://") || path.startsWith("file://")) {
        Uri.parse(path)
    } else {
        File(path)
    }
    val bitmap = Glide.with(context)
        .asBitmap()
        .load(model)
        .override(MAX_THUMBNAIL_SIZE)
        .submit()
        .get() ?: error("Failed to decode image with Glide: $path")
    return bitmap.asImageBitmap()
}

private const val MAX_THUMBNAIL_SIZE = 1080

@Composable
fun PhotoOfTheDayRoute(
    viewModel: PhotoOfTheDayViewModel = viewModel(factory = PhotoOfTheDayViewModel.Factory),
    onBack: () -> Unit,
    onPhotoSaved: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraHelper = remember { CameraHelper(context) }
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(uiState.isPhotoSaved) {
        if (uiState.isPhotoSaved) {
            val duration = viewModel.getSessionDurationSeconds()
            viewModel.onNavigatedToFeedback()
            onPhotoSaved(duration)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            viewModel.onPhotoCaptured(currentPhotoUri.toString())
        }
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onPhotoCaptured(uri.toString())
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = cameraHelper.createPictureUri()
            if (uri != null) {
                currentPhotoUri = uri
                takePictureLauncher.launch(uri)
            }
        }
    }

    val onTakePhoto = {
        if (cameraHelper.hasCameraPermission()) {
            val uri = cameraHelper.createPictureUri()
            if (uri != null) {
                currentPhotoUri = uri
                takePictureLauncher.launch(uri)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val onPickPhoto = {
        pickVisualMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        viewModel.onScreenRendered()
    }

    PhotoOfTheDayScreen(
        uiState = uiState,
        onBack = onBack,
        onTakePhotoClick = onTakePhoto,
        onPickPhotoClick = onPickPhoto,
        onSelectDay = viewModel::onSelectDay,
        onBackToToday = viewModel::onBackToToday,
        onShowDeleteDialog = viewModel::onShowDeleteDialog,
        onConfirmDelete = viewModel::onDeleteTodayPhoto,
        onRetakeClick = viewModel::onRetakeClick,
        onSaveClick = viewModel::onSaveClick
    )
}

private val previewWeek = listOf(
    PhotoDay("M", "Monday", PhotoDayState.TAKEN),
    PhotoDay("T", "Tuesday", PhotoDayState.MISSED),
    PhotoDay("W", "Wednesday", PhotoDayState.TAKEN),
    PhotoDay("T", "Thursday", PhotoDayState.TAKEN),
    PhotoDay("F", "Friday", PhotoDayState.TODAY),
    PhotoDay("S", "Saturday", PhotoDayState.UPCOMING),
    PhotoDay("S", "Sunday", PhotoDayState.UPCOMING)
)

@Preview(showBackground = true, name = "Photo of the day - empty")
@Composable
fun PhotoOfTheDayEmptyPreview() {
    AppTheme {
        PhotoOfTheDayScreen(
            uiState = PhotoOfTheDayUiState(todayLabel = "Friday, October 2", week = previewWeek, isLoading = false),
            onBack = {}, onTakePhotoClick = {}, onSelectDay = {}, onBackToToday = {},
            onShowDeleteDialog = {}, onConfirmDelete = {}, onRetakeClick = {}, onSaveClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Photo of the day - pending")
@Composable
fun PhotoOfTheDayPendingPreview() {
    AppTheme {
        PhotoOfTheDayScreen(
            uiState = PhotoOfTheDayUiState(
                todayLabel = "Friday, October 2", week = previewWeek, isLoading = false,
                pendingPhotoUri = "content://preview/photo.jpg"
            ),
            onBack = {}, onTakePhotoClick = {}, onSelectDay = {}, onBackToToday = {},
            onShowDeleteDialog = {}, onConfirmDelete = {}, onRetakeClick = {}, onSaveClick = {}
        )
    }
}
