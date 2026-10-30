package com.moviles.ark.domain.models

//que paso con un cambio del perfil o de la foto de perfil, para decirselo al usuario
enum class ProfileSaveStatus {
    //ya esta en la nube
    UPLOADED,
    //quedo guardado en el telefono (sin red o la red fallo); se sube solo cuando vuelva la conexion
    PENDING_UPLOAD,
    //otro telefono lo cambio despues: se dejo esa version y el cambio de este telefono no se aplico
    CHANGED_ELSEWHERE
}
