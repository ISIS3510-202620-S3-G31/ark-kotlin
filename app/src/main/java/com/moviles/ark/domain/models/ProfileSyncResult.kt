package com.moviles.ark.domain.models

//resultado de subir el perfil pendiente: cuantas partes subieron (datos, foto de perfil) y cuantas fallaron
data class ProfileSyncResult(
    val synced: Int = 0,
    val failed: Int = 0
)
