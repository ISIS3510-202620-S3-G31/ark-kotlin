package com.moviles.ark.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import com.moviles.ark.data.local.AvatarPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.ProfileUiState
import com.moviles.ark.ui.viewmodels.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

//solo se usan colores de la paleta (ms6); las tarjetas son el texto muy transparente sobre el fondo
private val CardFill = TextColor.copy(alpha = 0.06f)

//pantalla sin estado: solo dibuja lo que le llega en uiState y avisa los clics
@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onSignOutClick: () -> Unit,
    onRetryClick: () -> Unit,
    onUploadPhotoClick: () -> Unit = {},
    onAvatarSelect: (String) -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    onEditNameChange: (String) -> Unit = {},
    onEditBirthDateChange: (String) -> Unit = {},
    onEditDismiss: () -> Unit = {},
    onSaveProfileClick: () -> Unit = {}
) {
    //los avatares solo se ven al tocar la foto o dentro de "Edit profile"; el resto del tiempo no ocupan la pantalla
    var showAvatarPicker by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Your profile", style = MaterialTheme.typography.headlineLarge, color = TextColor)
            Spacer(modifier = Modifier.height(32.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator(color = SecondaryColor)
            } else if (uiState.errorMessage != null) {
                Text(uiState.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetryClick) { Text("Try again", style = MaterialTheme.typography.labelLarge, color = TextColor) }
            } else {
                //avatar: la foto que eligio el usuario o su avatar; al tocarlo se abre el selector de avatares
                Avatar(path = uiState.avatarPath, initials = uiState.initials, avatarId = uiState.selectedAvatarId, onClick = { showAvatarPicker = true })
                Spacer(modifier = Modifier.height(16.dp))
                Text(uiState.name, style = MaterialTheme.typography.headlineMedium, color = TextColor, textAlign = TextAlign.Center)
                Text(uiState.email, style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.7f))
                if (uiState.age > 0) {
                    Text("${uiState.age} years old", style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.7f))
                }
                if (uiState.memberSince.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Member since ${uiState.memberSince}", style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.7f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onEditProfileClick) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = TextColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit profile", style = MaterialTheme.typography.labelLarge, color = TextColor)
                }
                uiState.message?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodyLarge, color = SecondaryColor, textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(20.dp))

                //dos datos lado a lado: racha y dias usando la app
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        icon = Icons.Filled.Whatshot,
                        value = uiState.streakDays,
                        label = "day streak",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    StatCard(
                        icon = Icons.Filled.CalendarMonth,
                        value = uiState.daysWithArk,
                        label = if (uiState.daysWithArk == 1) "day with Ark" else "days with Ark",
                        modifier = Modifier.weight(1f)
                    )
                }

                //sin racha, en vez de solo mostrar un 0 se le dice al usuario como empezarla
                if (uiState.streakDays == 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Do a mood check-in today to start your streak.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            //cerrar sesion es una accion secundaria: borde secondary, texto en color de texto para que se lea bien
            OutlinedButton(
                onClick = onSignOutClick,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                border = BorderStroke(1.dp, SecondaryColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign out", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showAvatarPicker) {
        AvatarPickerDialog(
            selectedAvatarId = uiState.selectedAvatarId,
            onAvatarSelect = onAvatarSelect,
            onUploadPhotoClick = {
                showAvatarPicker = false
                onUploadPhotoClick()
            },
            onDismiss = { showAvatarPicker = false }
        )
    }

    if (uiState.isEditing) {
        EditProfileDialog(
            uiState = uiState,
            onAvatarSelect = onAvatarSelect,
            onNameChange = onEditNameChange,
            onBirthDateChange = onEditBirthDateChange,
            onDismiss = onEditDismiss,
            onSaveClick = onSaveProfileClick
        )
    }
}

//circulo secondary como el boton de perfil del home; la camarita avisa que se puede cambiar la foto
@Composable
private fun Avatar(
    path: String?,
    initials: String,
    avatarId: String,
    onClick: () -> Unit
) {
    val avatar = rememberAvatar(path)
    Box(modifier = Modifier.size(96.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(SecondaryColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (avatar != null) {
                Image(bitmap = avatar, contentDescription = "Profile photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Image(
                    painter = painterResource(AvatarPreferences.getDrawableRes(avatarId)),
                    contentDescription = "Profile avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .align(Alignment.BottomEnd)
                .background(PrimaryColor, CircleShape)
                .border(2.dp, BackgroundColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = "Change profile photo", tint = TextColor, modifier = Modifier.size(16.dp))
        }
    }
}

//dialogo que abre la foto de perfil: elegir un avatar o subir una foto propia
@Composable
private fun AvatarPickerDialog(
    selectedAvatarId: String,
    onAvatarSelect: (String) -> Unit,
    onUploadPhotoClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundColor,
        title = { Text("Choose your avatar", style = MaterialTheme.typography.headlineSmall, color = TextColor) },
        text = {
            AvatarSelectionCarousel(
                selectedAvatarId = selectedAvatarId,
                onAvatarSelect = onAvatarSelect
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", style = MaterialTheme.typography.labelLarge, color = TextColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onUploadPhotoClick) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = TextColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload photo", style = MaterialTheme.typography.labelLarge, color = TextColor)
            }
        }
    )
}

//fila horizontal de avatares con borde resaltado y check en el elegido
@Composable
private fun AvatarSelectionCarousel(
    selectedAvatarId: String,
    onAvatarSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
        ) {
            items(AvatarPreferences.PRESET_AVATARS) { preset ->
                val isSelected = preset.id == selectedAvatarId
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1.0f,
                    label = "avatar_preset_scale"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .scale(scale)
                        .clickable { onAvatarSelect(preset.id) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .shadow(
                                elevation = if (isSelected) 6.dp else 1.dp,
                                shape = CircleShape
                            )
                            .clip(CircleShape)
                            .background(if (isSelected) PrimaryColor.copy(alpha = 0.15f) else CardFill)
                            .border(
                                width = if (isSelected) 3.dp else 1.5.dp,
                                color = if (isSelected) PrimaryColor else TextColor.copy(alpha = 0.2f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(preset.drawableRes),
                            contentDescription = preset.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // check cuando esta elegido
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(PrimaryColor)
                                    .border(1.5.dp, BackgroundColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = TextColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) TextColor else TextColor.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

//el repositorio ya guarda el avatar pequeno y derecho, asi que aqui solo se lee el archivo fuera del hilo principal
@Composable
private fun rememberAvatar(path: String?): ImageBitmap? {
    val avatar by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    return avatar
}

//dialogo para cambiar nombre, fecha de nacimiento y avatar; el correo no se edita porque es el de la cuenta
//se edita la fecha y no la edad: la edad se calcula de la fecha y asi se actualiza sola con cada cumpleanos
@Composable
private fun EditProfileDialog(
    uiState: ProfileUiState,
    onAvatarSelect: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onBirthDateChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSaveClick: () -> Unit
) {
    val context = LocalContext.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SecondaryColor,
        unfocusedBorderColor = TextColor.copy(alpha = 0.3f),
        focusedLabelColor = TextColor,
        unfocusedLabelColor = TextColor.copy(alpha = 0.7f),
        cursorColor = TextColor
    )
    AlertDialog(
        onDismissRequest = { if (!uiState.isSaving) onDismiss() },
        containerColor = BackgroundColor,
        title = { Text("Edit profile", style = MaterialTheme.typography.headlineSmall, color = TextColor) },
        text = {
            Column {
                OutlinedTextField(
                    value = uiState.editName,
                    onValueChange = onNameChange,
                    enabled = !uiState.isSaving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextColor),
                    //se cambia solo la fuente: material anima el tamano de la etiqueta y usaria su letra por defecto
                    label = { Text("Name", style = LocalTextStyle.current.copy(fontFamily = FigtreeFontFamily)) },
                    colors = fieldColors
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.editBirthDate,
                    onValueChange = onBirthDateChange,
                    enabled = !uiState.isSaving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextColor),
                    label = { Text("Date of birth (YYYY-MM-DD)", style = LocalTextStyle.current.copy(fontFamily = FigtreeFontFamily)) },
                    //el calendario evita errores al escribir la fecha, igual que en el registro
                    trailingIcon = {
                        IconButton(onClick = { showBirthDatePicker(context, uiState.editBirthDate, onBirthDateChange) }, enabled = !uiState.isSaving) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Select birth date", tint = TextColor)
                        }
                    },
                    colors = fieldColors
                )
                if (uiState.editError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(uiState.editError, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Avatar", style = MaterialTheme.typography.bodyLarge, color = TextColor)
                Spacer(modifier = Modifier.height(8.dp))
                AvatarSelectionCarousel(
                    selectedAvatarId = uiState.selectedAvatarId,
                    onAvatarSelect = onAvatarSelect
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSaveClick,
                enabled = !uiState.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = TextColor)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TextColor, strokeWidth = 2.dp)
                } else {
                    Text("Save", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !uiState.isSaving) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = TextColor)
            }
        }
    )
}

//selector de fecha del sistema; abre en la fecha guardada o, si no hay, hace 20 anos, y devuelve YYYY-MM-DD
private fun showBirthDatePicker(context: Context, current: String, onPicked: (String) -> Unit) {
    val calendar = Calendar.getInstance()
    val saved = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(current) }.getOrNull()
    if (saved != null) calendar.time = saved else calendar.add(Calendar.YEAR, -20)
    DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day)) },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
}

//tarjeta de un dato: icono en cuadro secondary (como las tarjetas del home), numero y texto
@Composable
private fun StatCard(icon: ImageVector, value: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(CardFill, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(SecondaryColor, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TextColor, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("$value", style = MaterialTheme.typography.headlineMedium, color = TextColor)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.7f))
    }
}

//conecta la pantalla con el viewmodel
@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
    onSignedOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    //selector de fotos del sistema: no pide permisos y solo deja elegir imagenes
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onAvatarPicked(uri.toString())
    }

    //cada vez que se abre la pestana se refresca el perfil y se recalcula la racha
    LaunchedEffect(Unit) {
        viewModel.loadProfile()
        viewModel.loadStreak()
    }

    //el viewmodel no navega: solo marca isSignedOut y la pantalla reacciona
    LaunchedEffect(uiState.isSignedOut) {
        if (uiState.isSignedOut) onSignedOut()
    }

    //el aviso de "guardado" se quita solo
    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            delay(MESSAGE_MILLIS)
            viewModel.onMessageShown()
        }
    }

    ProfileScreen(
        uiState = uiState,
        onSignOutClick = viewModel::onSignOutClick,
        onRetryClick = viewModel::loadProfile,
        onUploadPhotoClick = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onAvatarSelect = viewModel::onAvatarSelected,
        onEditProfileClick = viewModel::onEditProfileClick,
        onEditNameChange = viewModel::onEditNameChange,
        onEditBirthDateChange = viewModel::onEditBirthDateChange,
        onEditDismiss = viewModel::onEditDismiss,
        onSaveProfileClick = viewModel::onSaveProfileClick
    )
}

private const val MESSAGE_MILLIS = 2_500L

@Preview(showBackground = true, name = "Profile with streak")
@Composable
fun ProfileScreenPreview() {
    AppTheme {
        ProfileScreen(
            uiState = ProfileUiState(
                name = "Laura Avelino",
                initials = "LA",
                email = "laura@uniandes.edu.co",
                age = 21,
                memberSince = "October 2026",
                daysWithArk = 12,
                streakDays = 3
            ),
            onSignOutClick = {},
            onRetryClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Profile without streak")
@Composable
fun ProfileScreenNoStreakPreview() {
    AppTheme {
        ProfileScreen(
            uiState = ProfileUiState(
                name = "lau3",
                initials = "L",
                email = "lau3@mail.com",
                memberSince = "October 2026",
                daysWithArk = 1
            ),
            onSignOutClick = {},
            onRetryClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Profile editing")
@Composable
fun ProfileScreenEditingPreview() {
    AppTheme {
        ProfileScreen(
            uiState = ProfileUiState(
                name = "Laura Avelino",
                initials = "LA",
                email = "laura@uniandes.edu.co",
                age = 21,
                memberSince = "October 2026",
                isEditing = true,
                editName = "Laura Avelino",
                editBirthDate = "2005-03-14"
            ),
            onSignOutClick = {},
            onRetryClick = {}
        )
    }
}
