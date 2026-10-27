package com.moviles.ark.ui.components

import androidx.annotation.DrawableRes
import com.moviles.ark.R
import com.moviles.ark.domain.models.Avatar

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
