package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.CookieListItem
import com.moviles.ark.domain.models.CookieModel
import kotlinx.coroutines.flow.Flow

//galletas del cookie jar del usuario con sesion
//todo se escribe primero en el telefono (room + archivo privado), asi crear, comer, devolver y borrar funcionan sin red
interface CookieRepository {
    //todas las galletas del usuario (activas y comidas); vuelve a emitir cada vez que cambia algo
    fun observeCookies(): Flow<List<CookieModel>>

    //listado liviano (sin el texto) para el bottom sheet
    fun observeCookieList(): Flow<List<CookieListItem>>

    suspend fun getCookie(cookieId: String): CookieModel?

    //titulo con el que se guardaria: el mismo, o con sufijo "-N" si ya existe
    suspend fun resolveTitle(title: String): String

    //crea una galleta activa; imageUri es la imagen elegida (camara o galeria) o null
    //la imagen se comprime una sola vez y se guarda en privado; sin espacio falla con NotEnoughSpaceException
    suspend fun createCookie(title: String, text: String, imageUri: String?): Result<CookieModel>

    //la galleta sale del jarro y pasa al historial (no se borra)
    suspend fun eatCookie(cookieId: String): Result<CookieModel>

    //una galleta comida vuelve al jarro
    suspend fun returnToJar(cookieId: String): Result<CookieModel>

    //borra para siempre una galleta comida: la fila de room y su archivo privado
    suspend fun deleteForever(cookieId: String): Result<Unit>

    //anota cuando se abrio, para borrar primero las imagenes menos usadas
    suspend fun markOpened(cookieId: String)

    //uid del usuario con sesion (semilla del sorteo del dia); null sin sesion
    fun currentUserId(): String?

    //true si el usuario ya dio permiso de camara
    fun hasCameraPermission(): Boolean

    //crea el archivo temporal donde la camara deja la foto y devuelve su direccion; null si no se pudo crear
    fun createCaptureUri(): String?

    //borra la foto temporal de la camara: la galleta ya guardo su propia copia comprimida
    suspend fun discardCapture(uri: String)
}
