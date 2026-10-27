package com.moviles.ark.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import com.bumptech.glide.Glide
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.moviles.ark.data.local.sensors.CameraHelper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.moviles.ark.ui.theme.ErrorColor
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.SuccessColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.PhotoOfTheDayUiState
import com.moviles.ark.ui.viewmodels.PhotoOfTheDayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    onSaveClick: () -> Unit,
    onShowDeleteDialog: (Boolean) -> Unit = {},
    onConfirmDelete: () -> Unit = {}
) {
    //dias con foto en orden (el mas viejo primero); son las paginas del carrusel
    val photoDays = uiState.week.filter { it.photo != null }
    val pagerState = rememberPagerState(initialPage = (photoDays.size - 1).coerceAtLeast(0)) { photoDays.size }
    val scope = rememberCoroutineScope()
    val carouselRequester = remember { BringIntoViewRequester() }

    //cuando llega una foto nueva (o un retake) el carrusel se va a la mas reciente
    LaunchedEffect(photoDays.size, uiState.todayPhoto?.id) {
        if (photoDays.isNotEmpty()) pagerState.scrollToPage(photoDays.size - 1)
    }

    //dia que se ve en el carrusel, para resaltarlo en la tira
    val shownDay = photoDays.getOrNull(pagerState.currentPage)

    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                //deja libre la barra de gestos de android para que el nombre del dia del carrusel no quede debajo
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Header(todayLabel = uiState.todayLabel, onBack = onBack)
            Spacer(modifier = Modifier.height(20.dp))
            WeekStrip(
                week = uiState.week,
                shownDay = shownDay,
                //tocar un chulito lleva el carrusel a la foto de ese dia
                onDayClick = { day ->
                    val page = photoDays.indexOf(day)
                    if (page >= 0) {
                        scope.launch {
                            carouselRequester.bringIntoView()
                            pagerState.animateScrollToPage(page)
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(24.dp))

            val pendingPhotoUri = uiState.pendingPhotoUri
            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SecondaryColor)
                }
                //foto recien tomada guardandose (tambien al retomar la de hoy)
                pendingPhotoUri != null -> PendingPhotoSection(
                    uri = pendingPhotoUri,
                    caption = uiState.caption,
                    isSaving = uiState.isSaving,
                    onCaptionChange = onCaptionChange,
                    onRetakeClick = onRetakeClick,
                    onSaveClick = onSaveClick
                )
                else -> {
                    //sin foto hoy: primero el espacio para tomarla y debajo el carrusel de los dias pasados
                    if (uiState.todayPhoto == null) {
                        EmptyPhotoSection(
                            onTakePhotoClick = onTakePhotoClick,
                            onPickPhotoClick = onPickPhotoClick
                        )
                    }
                    if (photoDays.isNotEmpty()) {
                        if (uiState.todayPhoto == null) Spacer(modifier = Modifier.height(32.dp))
                        PhotoCarousel(
                            photoDays = photoDays,
                            pagerState = pagerState,
                            modifier = Modifier.bringIntoViewRequester(carouselRequester)
                        )
                        //solo la foto de hoy se puede retomar; las de dias pasados solo se miran
                        if (shownDay?.isToday == true) {
                            Spacer(modifier = Modifier.height(16.dp))
                            TodayPhotoActions(
                                onRetakePhotoClick = onTakePhotoClick,
                                onDeleteClick = { onShowDeleteDialog(true) }
                            )
                        }
                    }
                }
            }

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(uiState.errorMessage, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (uiState.showDeleteDialog) {
        DeletePhotoDialog(onDismiss = { onShowDeleteDialog(false) }, onConfirm = onConfirmDelete)
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

//tira de los ultimos 7 dias con el estado de cada uno; el ultimo es hoy
//los dias con foto se pueden tocar y el que se ve en el carrusel lleva un anillo secondary
@Composable
private fun WeekStrip(week: List<PhotoDay>, shownDay: PhotoDay?, onDayClick: (PhotoDay) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEach { day -> DayDot(day = day, isShown = day == shownDay, onClick = { onDayClick(day) }) }
    }
}

@Composable
private fun DayDot(day: PhotoDay, isShown: Boolean, onClick: () -> Unit) {
    val stateText = when (day.state) {
        PhotoDayState.TAKEN -> "photo taken"
        PhotoDayState.TODAY -> "today"
        PhotoDayState.MISSED -> "no photo"
    }
    val canOpen = day.photo != null
    Column(
        //el lector de pantalla lee "Monday, photo taken" en vez de la letra sola
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (canOpen) Modifier.clickable(role = Role.Button, onClickLabel = "Show photo", onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = "${day.name}, $stateText" }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        //anillo secondary alrededor del dia que se esta viendo en el carrusel
        Box(
            modifier = Modifier
                .size(42.dp)
                .border(2.dp, if (isShown) SecondaryColor else BackgroundColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when (day.state) {
                //success: la paleta lo reserva para tareas completadas
                PhotoDayState.TAKEN -> Box(
                    modifier = Modifier.size(34.dp).background(SuccessColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = TextColor, modifier = Modifier.size(16.dp))
                }
                //primary: lo importante de hoy
                PhotoDayState.TODAY -> Box(
                    modifier = Modifier.size(34.dp).border(2.dp, PrimaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(10.dp).background(PrimaryColor, CircleShape))
                }
                //dia sin foto: x en color de texto sobre el mismo circulo suave, sin rojo para no castigar al usuario
                PhotoDayState.MISSED -> Box(
                    modifier = Modifier.size(34.dp).background(TextColor.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = TextColor, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            day.shortLabel,
            style = MaterialTheme.typography.labelLarge,
            color = if (day.isToday) TextColor else TextColor.copy(alpha = 0.7f)
        )
    }
}

//carrusel con las fotos de los ultimos 7 dias que existen; debajo dice de que dia es y en que pagina va
@Composable
private fun PhotoCarousel(photoDays: List<PhotoDay>, pagerState: PagerState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val day = photoDays[page]
            day.photo?.let { photo ->
                PhotoThumbnail(
                    path = photo.localFilePath,
                    description = if (day.isToday) "Today's photo" else "Photo from ${day.name}",
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        val shown = photoDays.getOrNull(pagerState.currentPage)
        Text(
            text = if (shown?.isToday == true) "Today" else shown?.name.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            color = TextColor
        )
        //puntos de pagina solo si hay mas de una foto
        if (photoDays.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                photoDays.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (index == pagerState.currentPage) SecondaryColor else TextColor.copy(alpha = 0.2f),
                                CircleShape
                            )
                    )
                }
            }
        }
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

//foto recien tomada: vista previa, guardar o repetir sin caption
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

//la foto que se ve es la de hoy: etiqueta de guardada y botones para retomarla o borrarla
@Composable
private fun TodayPhotoActions(onRetakePhotoClick: (() -> Unit)?, onDeleteClick: () -> Unit) {
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
        OutlinedButton(
            onClick = { onRetakePhotoClick?.invoke() },
            enabled = onRetakePhotoClick != null,
            modifier = Modifier.weight(1f).height(50.dp),
            border = BorderStroke(1.dp, SecondaryColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = TextColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retake", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(modifier = Modifier.width(12.dp))
        //borrar es destructivo: borde y texto en el color de error de la paleta
        OutlinedButton(
            onClick = onDeleteClick,
            modifier = Modifier.weight(1f).height(50.dp),
            border = BorderStroke(1.dp, ErrorColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorColor)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, tint = ErrorColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete", style = MaterialTheme.typography.labelLarge)
        }
    }
}

//confirmacion antes de borrar la foto de hoy, porque no se puede deshacer
@Composable
private fun DeletePhotoDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundColor,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Delete photo?", style = MaterialTheme.typography.headlineSmall, color = TextColor) },
        text = {
            Text(
                "Are you sure you want to delete today's photo? This action cannot be undone.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextColor
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorColor, contentColor = BackgroundColor)
            ) {
                Text("Delete", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, SecondaryColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

//miniatura de la foto: se lee en segundo plano y reducida, para no trabar la pantalla ni gastar memoria
@Composable
private fun PhotoThumbnail(path: String, modifier: Modifier = Modifier, description: String = "Photo of the day") {
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
            bitmap != null -> Image(bitmap, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            result == null -> CircularProgressIndicator(color = SecondaryColor)
            else -> Icon(Icons.Filled.BrokenImage, contentDescription = "The photo could not be opened", tint = TextColor.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
        }
    }
}

//glide lee la foto (content:// de la galeria o ruta de archivo) en segundo plano, reducida y ya girada segun el exif
private fun decodeThumbnail(context: Context, path: String): ImageBitmap {
    val model: Any = if (path.startsWith("content://") || path.startsWith("file://")) Uri.parse(path) else File(path)
    val bitmap = Glide.with(context)
        .asBitmap()
        .load(model)
        .override(MAX_THUMBNAIL_SIZE)
        .submit()
        .get() ?: error("Could not decode $path")
    return bitmap.asImageBitmap()
}

private const val MAX_THUMBNAIL_SIZE = 1080

//conecta la pantalla con el viewmodel
@Composable
fun PhotoOfTheDayRoute(
    viewModel: PhotoOfTheDayViewModel = viewModel(factory = PhotoOfTheDayViewModel.Factory),
    onBack: () -> Unit,
    onPhotoSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraHelper = remember { CameraHelper(context) }
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(uiState.isPhotoSaved) {
        if (uiState.isPhotoSaved) {
            viewModel.onNavigatedToFeedback()
            onPhotoSaved()
        }
    }

    //launcher para capturar foto con la camara del dispositivo
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            viewModel.onPhotoCaptured(currentPhotoUri.toString())
        }
    }

    //launcher para seleccionar foto de la galeria mediante PickVisualMedia
    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onPhotoCaptured(uri.toString())
        }
    }

    //launcher para solicitar el permiso de camara al usuario
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

    //latencia: se espera al siguiente cuadro; para entonces el primer dibujo de la pantalla ya termino
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
        onSaveClick = viewModel::onSaveClick,
        onShowDeleteDialog = viewModel::onShowDeleteDialog,
        onConfirmDelete = viewModel::onDeleteTodayPhoto
    )
}

private val previewWeek = listOf(
    PhotoDay("S", "Saturday", PhotoDayState.MISSED),
    PhotoDay("S", "Sunday", PhotoDayState.TAKEN),
    PhotoDay("M", "Monday", PhotoDayState.TAKEN),
    PhotoDay("T", "Tuesday", PhotoDayState.MISSED),
    PhotoDay("W", "Wednesday", PhotoDayState.TAKEN),
    PhotoDay("T", "Thursday", PhotoDayState.TAKEN),
    PhotoDay("F", "Friday", PhotoDayState.TODAY, isToday = true)
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
