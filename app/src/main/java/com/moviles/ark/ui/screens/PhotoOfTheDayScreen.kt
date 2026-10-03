package com.moviles.ark.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.moviles.ark.data.local.sensors.CameraHelper
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.moviles.ark.domain.models.PhotoDay
import com.moviles.ark.domain.models.PhotoDayState
import com.moviles.ark.domain.models.PhotoEntryModel
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.SuccessColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.PhotoOfTheDayUiState
import com.moviles.ark.ui.viewmodels.PhotoOfTheDayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.InputStream

//solo se usan colores de la paleta (ms6); las tarjetas son el texto muy transparente sobre el fondo
private val CardFill = TextColor.copy(alpha = 0.06f)

//pantalla sin estado: solo dibuja lo que le llega en uiState y avisa los clics
@Composable
fun PhotoOfTheDayScreen(
    uiState: PhotoOfTheDayUiState,
    onBack: () -> Unit,
    onTakePhotoClick: (() -> Unit)?,
    onPickPhotoClick: (() -> Unit)? = null,
    onCaptionChange: (String) -> Unit,
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
            WeekStrip(week = uiState.week)
            Spacer(modifier = Modifier.height(24.dp))

            //el orden importa: primero lo que esta cargando, luego la foto de hoy, luego la pendiente
            val todayPhoto = uiState.todayPhoto
            val pendingPhotoUri = uiState.pendingPhotoUri
            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SecondaryColor)
                }
                todayPhoto != null -> SavedPhotoSection(photo = todayPhoto)
                pendingPhotoUri != null -> PendingPhotoSection(
                    uri = pendingPhotoUri,
                    caption = uiState.caption,
                    isSaving = uiState.isSaving,
                    onCaptionChange = onCaptionChange,
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
}

@Composable
private fun Header(todayLabel: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        //boton redondo de 48 dp (minimo tactil), color secondary como el resto de la navegacion
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

//tira de lunes a domingo con el estado de cada dia
@Composable
private fun WeekStrip(week: List<PhotoDay>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEach { day -> DayDot(day) }
    }
}

@Composable
private fun DayDot(day: PhotoDay) {
    val stateText = when (day.state) {
        PhotoDayState.TAKEN -> "photo taken"
        PhotoDayState.TODAY -> "today"
        PhotoDayState.MISSED -> "no photo"
        PhotoDayState.UPCOMING -> "not yet"
    }
    Column(
        //el lector de pantalla lee "Monday, photo taken" en vez de la letra sola
        modifier = Modifier.clearAndSetSemantics { contentDescription = "${day.name}, $stateText" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (day.state) {
            //success: la paleta lo reserva para tareas completadas
            PhotoDayState.TAKEN -> Box(
                modifier = Modifier.size(36.dp).background(SuccessColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = TextColor, modifier = Modifier.size(16.dp))
            }
            //primary: lo importante de hoy
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

//todavia no hay foto hoy: visor vacio y botones para tomarla o seleccionarla
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
    if (onTakePhotoClick == null && onPickPhotoClick == null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "The camera will be available soon.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextColor.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

//foto recien tomada: vista previa, nota opcional, repetir o guardar
@Composable
private fun PendingPhotoSection(
    uri: String,
    caption: String,
    isSaving: Boolean,
    onCaptionChange: (String) -> Unit,
    onRetakeClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    PhotoThumbnail(path = uri, modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f))
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = caption,
        onValueChange = onCaptionChange,
        enabled = !isSaving,
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextColor),
        //se cambia solo la fuente: material anima el tamano de la etiqueta y usaria su letra por defecto
        label = { Text("Add a note (optional)", style = LocalTextStyle.current.copy(fontFamily = FigtreeFontFamily)) },
        supportingText = {
            Text(
                "${caption.length} / ${PhotoEntryModel.MAX_CAPTION_LENGTH}",
                style = LocalTextStyle.current.copy(fontFamily = FigtreeFontFamily),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SecondaryColor,
            unfocusedBorderColor = TextColor.copy(alpha = 0.3f),
            focusedLabelColor = TextColor,
            unfocusedLabelColor = TextColor.copy(alpha = 0.7f),
            cursorColor = TextColor
        )
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        //repetir es secundario: borde secondary y texto oscuro para que se lea bien
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
        //guardar es la accion principal: primary
        Button(
            onClick = onSaveClick,
            enabled = !isSaving,
            modifier = Modifier.weight(1f).height(50.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

//ya hay foto hoy: se muestra con su nota (una foto por dia)
@Composable
private fun SavedPhotoSection(photo: PhotoEntryModel) {
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
    if (photo.caption.isNotBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(photo.caption, style = MaterialTheme.typography.bodyLarge, color = TextColor)
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text("Come back tomorrow for a new moment.", style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.7f))
}

//miniatura de la foto: se lee en segundo plano y reducida, para no trabar la pantalla ni gastar memoria
@Composable
private fun PhotoThumbnail(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    //null mientras carga; despues trae la imagen o el error
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

//abre la foto desde una direccion content:// o file:// (camara) o desde una ruta de archivo (#26)
private fun openPhoto(context: Context, path: String): InputStream {
    return if (path.startsWith("content://") || path.startsWith("file://")) {
        context.contentResolver.openInputStream(Uri.parse(path)) ?: error("Cannot open $path")
    } else {
        FileInputStream(path)
    }
}

//lee la foto a un tamano maximo de 1080 px y la gira segun lo que guardo la camara (exif)
private fun decodeThumbnail(context: Context, path: String): ImageBitmap {
    //primero solo se leen las medidas, sin cargar la imagen
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openPhoto(context, path).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= MAX_THUMBNAIL_SIZE || bounds.outHeight / (sample * 2) >= MAX_THUMBNAIL_SIZE) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = openPhoto(context, path).use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("Not an image: $path")

    val rotation = openPhoto(context, path).use { stream ->
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }
    val upright = if (rotation == 0f) {
        bitmap
    } else {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
    }
    return upright.asImageBitmap()
}

private const val MAX_THUMBNAIL_SIZE = 1080

//conecta la pantalla con el viewmodel
@Composable
fun PhotoOfTheDayRoute(
    viewModel: PhotoOfTheDayViewModel = viewModel(factory = PhotoOfTheDayViewModel.Factory),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraHelper = remember { CameraHelper(context) }
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }

    //launcher para capturar foto con la camara del dispositivo (#31)
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            viewModel.onPhotoCaptured(currentPhotoUri.toString())
        }
    }

    //launcher para seleccionar foto de la galeria mediante PickVisualMedia (#31)
    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onPhotoCaptured(uri.toString())
        }
    }

    //launcher para solicitar el permiso de camara al usuario (#31)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = cameraHelper.createTempPictureUri()
            if (uri != null) {
                currentPhotoUri = uri
                takePictureLauncher.launch(uri)
            }
        }
    }

    val onTakePhoto = {
        if (cameraHelper.hasCameraPermission()) {
            val uri = cameraHelper.createTempPictureUri()
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

    //#8: se espera al siguiente cuadro; para entonces el primer dibujo de la pantalla ya termino
    LaunchedEffect(Unit) {
        withFrameNanos { }
        viewModel.onScreenRendered()
    }

    PhotoOfTheDayScreen(
        uiState = uiState,
        onBack = onBack,
        onTakePhotoClick = onTakePhoto,
        onPickPhotoClick = onPickPhoto,
        onCaptionChange = viewModel::onCaptionChange,
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
            onBack = {}, onTakePhotoClick = {}, onCaptionChange = {}, onRetakeClick = {}, onSaveClick = {}
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
                pendingPhotoUri = "content://preview/photo.jpg", caption = "Sunset from the library"
            ),
            onBack = {}, onTakePhotoClick = {}, onCaptionChange = {}, onRetakeClick = {}, onSaveClick = {}
        )
    }
}
