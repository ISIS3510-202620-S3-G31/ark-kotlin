package com.moviles.ark.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import com.moviles.ark.ui.components.UserAvatarImage
import com.moviles.ark.ui.components.avatarDrawable
import com.moviles.ark.ui.components.presetAvatars
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.ScreenTopPadding
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.ProfileUiState
import com.moviles.ark.ui.viewmodels.ProfileViewModel
import kotlinx.coroutines.delay
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
    onPhotoSelect: () -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    onEditNameChange: (String) -> Unit = {},
    onEditBirthDateChange: (String) -> Unit = {},
    onEditDismiss: () -> Unit = {},
    onSaveProfileClick: () -> Unit = {},
    onConfirmSignOut: () -> Unit = {},
    onDismissSignOut: () -> Unit = {}
) {
    //los avatares solo se ven al tocar la foto o dentro de "Edit profile"; el resto del tiempo no ocupan la pantalla
    var showAvatarPicker by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = ScreenTopPadding, bottom = 24.dp),
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
                //imagen de perfil: la foto o el avatar, segun lo que eligio; al tocarla se abre el selector
                Avatar(
                    path = uiState.avatarPath.takeIf { uiState.showsPhoto },
                    initials = uiState.initials,
                    avatarId = uiState.selectedAvatarId,
                    onClick = { showAvatarPicker = true }
                )
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
            uiState = uiState,
            onAvatarSelect = onAvatarSelect,
            onPhotoSelect = onPhotoSelect,
            onUploadPhotoClick = {
                showAvatarPicker = false
                onUploadPhotoClick()
            },
            onDismiss = { showAvatarPicker = false }
        )
    }

    //quedan cosas sin subir: al cerrar sesion se quedan en este telefono hasta que esta cuenta vuelva a entrar
    if (uiState.pendingBeforeSignOut > 0) {
        val count = uiState.pendingBeforeSignOut
        AlertDialog(
            onDismissRequest = onDismissSignOut,
            containerColor = BackgroundColor,
            title = { Text("Not uploaded yet", style = MaterialTheme.typography.headlineSmall, color = TextColor) },
            text = {
                Text(
                    "${if (count == 1) "1 change is" else "$count changes are"} saved only on this phone. " +
                        "Connect to the internet before signing out, or they will upload the next time you sign in on this phone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextColor
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissSignOut) {
                    Text("Stay signed in", style = MaterialTheme.typography.labelLarge, color = TextColor)
                }
            },
            dismissButton = {
                TextButton(onClick = onConfirmSignOut) {
                    Text("Sign out anyway", style = MaterialTheme.typography.labelLarge, color = TextColor)
                }
            }
        )
    }

    if (uiState.isEditing) {
        EditProfileDialog(
            uiState = uiState,
            onAvatarSelect = onAvatarSelect,
            onPhotoSelect = onPhotoSelect,
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
    Box(modifier = Modifier.size(96.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(SecondaryColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            UserAvatarImage(photoPath = path, avatarId = avatarId, modifier = Modifier.fillMaxSize())
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

//dialogo que abre la foto de perfil: elegir la foto propia, un avatar o subir otra foto
@Composable
private fun AvatarPickerDialog(
    uiState: ProfileUiState,
    onAvatarSelect: (String) -> Unit,
    onPhotoSelect: () -> Unit,
    onUploadPhotoClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundColor,
        title = { Text("Choose your picture", style = MaterialTheme.typography.headlineSmall, color = TextColor) },
        text = {
            AvatarSelectionCarousel(
                uiState = uiState,
                onAvatarSelect = onAvatarSelect,
                onPhotoSelect = onPhotoSelect
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
                Text(
                    if (uiState.avatarPath == null) "Upload photo" else "New photo",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextColor
                )
            }
        }
    )
}

//fila horizontal con la foto subida (si hay) y los avatares; borde resaltado y check en el elegido
//elegir la foto o un avatar no borra el otro: se puede cambiar entre los dos cuando se quiera
@Composable
private fun AvatarSelectionCarousel(
    uiState: ProfileUiState,
    onAvatarSelect: (String) -> Unit,
    onPhotoSelect: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
    ) {
        uiState.avatarPath?.let { photoPath ->
            item(key = "photo") {
                AvatarOption(label = "Your photo", isSelected = uiState.showsPhoto, onClick = onPhotoSelect) {
                    UserAvatarImage(
                        photoPath = photoPath,
                        avatarId = uiState.selectedAvatarId,
                        photoDescription = "Your photo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        items(presetAvatars, key = { it.id }) { preset ->
            AvatarOption(
                label = preset.name,
                isSelected = !uiState.showsPhoto && preset.id == uiState.selectedAvatarId,
                onClick = { onAvatarSelect(preset.id) }
            ) {
                Image(
                    painter = painterResource(avatarDrawable(preset.id)),
                    contentDescription = preset.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

//una opcion del selector: circulo con la imagen, check si esta elegida y el nombre debajo
@Composable
private fun AvatarOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    image: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        label = "avatar_preset_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable(onClick = onClick)
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
            image()

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
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) TextColor else TextColor.copy(alpha = 0.75f)
        )
    }
}

//dialogo para cambiar nombre, fecha de nacimiento y avatar; el correo no se edita porque es el de la cuenta
//se edita la fecha y no la edad: la edad se calcula de la fecha y asi se actualiza sola con cada cumpleanos
@Composable
private fun EditProfileDialog(
    uiState: ProfileUiState,
    onAvatarSelect: (String) -> Unit,
    onPhotoSelect: () -> Unit,
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
                Text("Profile picture", style = MaterialTheme.typography.bodyLarge, color = TextColor)
                Spacer(modifier = Modifier.height(8.dp))
                AvatarSelectionCarousel(
                    uiState = uiState,
                    onAvatarSelect = onAvatarSelect,
                    onPhotoSelect = onPhotoSelect
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
        onConfirmSignOut = viewModel::onConfirmSignOut,
        onDismissSignOut = viewModel::onDismissSignOut,
        onRetryClick = viewModel::loadProfile,
        onUploadPhotoClick = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onAvatarSelect = viewModel::onAvatarSelected,
        onPhotoSelect = viewModel::onPhotoSelected,
        onEditProfileClick = viewModel::onEditProfileClick,
        onEditNameChange = viewModel::onEditNameChange,
        onEditBirthDateChange = viewModel::onEditBirthDateChange,
        onEditDismiss = viewModel::onEditDismiss,
        onSaveProfileClick = viewModel::onSaveProfileClick
    )
}

//los avisos de "guardado en el telefono" son mas largos: se dejan un poco mas para alcanzar a leerlos
private const val MESSAGE_MILLIS = 4_000L

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
