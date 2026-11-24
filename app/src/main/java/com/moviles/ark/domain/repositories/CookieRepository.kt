package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.CookieListItem
import com.moviles.ark.domain.models.CookieRefreshResult
import com.moviles.ark.domain.models.CookieUploadResult
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

    //aplica el presupuesto de imagenes del telefono (50/15 mb) desalojando por lru las ya subidas
    //protectedIds son las del jarro de hoy, que nunca se desalojan
    suspend fun enforceImageBudget(protectedIds: Set<String>)

    //cuantas galletas del usuario faltan por subir a la nube; vuelve a emitir con cada cambio
    fun observePendingUploads(): Flow<Int>

    //cached then network: trae de la nube los metadatos (sin imagenes) y los junta con room
    //llegan las de otros telefonos, se borran las que se borraron en otro, gana el cambio mas nuevo y se resuelven
    //los titulos repetidos; sin red o con timeout falla y room queda como estaba
    suspend fun refreshFromCloud(): Result<CookieRefreshResult>

    //true si este telefono ya bajo alguna vez las galletas de la cuenta; false en un telefono nuevo
    fun hasDownloadedFromCloud(): Boolean

    //baja de la nube la imagen de una galleta que no esta en el telefono y la guarda en privado
    //devuelve la galleta con su ruta local; si falla no queda ningun archivo a medias
    suspend fun downloadImage(cookieId: String): Result<CookieModel>

    //sube las galletas pendientes (primero la imagen, luego el documento); lo llama el sync en segundo plano
    suspend fun uploadPending(): CookieUploadResult

    //cuantas galletas del usuario con sesion faltan por subir (aviso antes de cerrar sesion)
    suspend fun pendingUploadCount(): Int

    //borra la copia de la nube de una galleta borrada en el telefono (primero el documento, luego la imagen)
    suspend fun deleteRemoteCopy(userId: String, cookieId: String): Result<Unit>

    //uid del usuario con sesion (semilla del sorteo del dia); null sin sesion
    fun currentUserId(): String?

    //true si el usuario ya dio permiso de camara
    fun hasCameraPermission(): Boolean

    //crea el archivo temporal donde la camara deja la foto y devuelve su direccion; null si no se pudo crear
    fun createCaptureUri(): String?

    //borra la foto temporal de la camara: la galleta ya guardo su propia copia comprimida
    suspend fun discardCapture(uri: String)
}
