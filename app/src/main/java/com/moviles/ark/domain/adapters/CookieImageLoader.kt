package com.moviles.ark.domain.adapters

import com.moviles.ark.domain.models.CookieModel

//carga la imagen de una galleta lista para pintar, sin que la pantalla sepa donde esta guardada
//Image es el tipo de imagen de la plataforma (Bitmap en android); el dominio no depende de android
interface CookieImageLoader<Image> {
    //null si la galleta no tiene imagen o si su archivo no esta en este telefono
    //orden de lectura: memoria (cache) y luego el archivo privado
    suspend fun load(cookie: CookieModel): Image?

    //deja en memoria las imagenes de las galletas del jarro de hoy, para abrirlas sin esperar
    suspend fun prefetch(dailyCookies: List<CookieModel>) {}

    //saca de memoria la imagen de una galleta borrada
    fun forget(cookieId: String) {}

    //vista previa pequena de la imagen elegida al crear la galleta (camara o galeria), antes de guardarla
    suspend fun loadPreview(sourceUri: String): Image?
}
