package com.moviles.ark.domain.models

//datos del usuario que se muestran en el perfil
//es distinto de User: User sirve para registrarse y trae la clave, este nunca la tiene
//el repositorio lo arma desde room (UserEntity, #119), asi se ve sin internet
data class UserProfile(
    val name: String,
    val email: String,
    //fecha de creacion de la cuenta en milisegundos (asi la entrega firebase auth)
    val memberSince: Long,
    //0 si todavia no se conoce
    val age: Int = 0,
    //racha guardada la ultima vez que se calculo; CheckInStreak.stillAlive dice si sigue valiendo hoy
    val streakDays: Int = 0,
    //fecha en milisegundos del ultimo check-in contado en la racha
    val lastCheckInAt: Long? = null,
    //ruta del avatar en el telefono; null si el usuario no ha elegido foto
    val avatarPath: String? = null
) {
    //reglas de negocio para editar el perfil: las mismas del registro (UserModel)
    fun isValidName(): Boolean {
        return name.trim().length in MIN_NAME_LENGTH..MAX_NAME_LENGTH
    }

    fun isValidAge(): Boolean {
        return age in 1..120
    }

    companion object {
        const val MIN_NAME_LENGTH = 2
        //las reglas de firestore no dejan guardar nombres de mas de 100 letras
        const val MAX_NAME_LENGTH = 100
    }
}
