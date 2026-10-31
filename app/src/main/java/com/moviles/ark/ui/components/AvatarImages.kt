package com.moviles.ark.ui.components

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.moviles.ark.R
import com.moviles.ark.domain.models.Avatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

//imagen de cada avatar del dominio; es cosa de la ui porque son recursos de android
@DrawableRes
fun avatarDrawable(avatarId: String?): Int = when (avatarId) {
    "avatar_2" -> R.drawable.avatar_2
    "avatar_3" -> R.drawable.avatar_3
    "avatar_4" -> R.drawable.avatar_4
    "avatar_5" -> R.drawable.avatar_5
    "avatar_6" -> R.drawable.avatar_6
    else -> R.drawable.avatar_1
}

//los avatares para elegir, en el orden del dominio
val presetAvatars: List<Avatar> get() = Avatar.PRESETS

//imagen de perfil del usuario: la foto que subio si existe y si no el avatar que eligio
//la usan el home y el perfil, asi las dos pantallas muestran lo mismo
@Composable
fun UserAvatarImage(
    photoPath: String?,
    avatarId: String,
    modifier: Modifier = Modifier,
    photoDescription: String = "Profile photo",
    avatarDescription: String = "Profile avatar"
) {
    val photo = rememberAvatarPhoto(photoPath)
    if (photo != null) {
        Image(bitmap = photo, contentDescription = photoDescription, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Image(
            painter = painterResource(avatarDrawable(avatarId)),
            contentDescription = avatarDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

//el repositorio ya guarda la foto pequena y derecha, asi que aqui solo se lee el archivo fuera del hilo principal
@Composable
private fun rememberAvatarPhoto(path: String?): ImageBitmap? {
    val photo by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    return photo
}
