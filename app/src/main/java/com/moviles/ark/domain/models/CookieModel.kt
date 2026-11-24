package com.moviles.ark.domain.models

import java.text.Normalizer
import java.util.Locale

//estado de una galleta: en el jarro o comida (pasa al historial, no se borra)
enum class CookieState { ACTIVE, EATEN }

//una galleta del cookie jar: un recuerdo o logro con titulo, texto e imagen opcional
//las reglas de titulos, limites y estados viven aqui para que la pantalla y el repositorio usen las mismas
data class CookieModel(
    //uuid generado en el telefono: el mismo id sirve en room, en el archivo privado y en la nube
    val id: String,
    val title: String,
    val text: String,
    val state: CookieState = CookieState.ACTIVE,
    //true si la galleta tiene imagen, aunque el archivo no este en el telefono (se desalojo o esta en la nube)
    val hasImage: Boolean = false,
    //ruta del jpg en el almacenamiento privado; null si no tiene o si no esta en este telefono
    val localImagePath: String? = null,
    //url de la imagen en la nube; null hasta que se suba
    val remoteImageUrl: String? = null,
    //ultima vez que se abrio, para borrar primero las imagenes menos usadas
    val lastOpenedAt: Long = 0L,
    //username de quien la regalo; null si es propia
    val receivedFrom: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    //false mientras haya algo por subir
    val isSynced: Boolean = false,
) {
    val titleKey: String get() = titleKeyOf(title)

    val isActive: Boolean get() = state == CookieState.ACTIVE

    //solo las del historial (comidas) se pueden borrar para siempre
    fun canBeDeletedForever(): Boolean = state == CookieState.EATEN

    //la imagen se puede mostrar sin red solo si su archivo sigue en el telefono
    fun hasLocalImage(): Boolean = hasImage && localImagePath != null

    companion object {
        const val MAX_TITLE_LENGTH = 60
        //el mismo limite que la nota del check-in y el comentario del feedback
        const val MAX_TEXT_LENGTH = 280

        const val EMPTY_TITLE_ERROR = "Give your cookie a title"
        const val EMPTY_TEXT_ERROR = "Write what you want to remember"
        const val CANNOT_DELETE_ERROR = "Only eaten cookies can be deleted forever"

        private val COMBINING_MARKS = Regex("\\p{Mn}+")
        private val SPACES = Regex("\\s+")

        //titulo normalizado para comparar repetidos: sin espacios de mas, sin tildes y en minusculas
        //"Mi Grado" y "mí grado " dan la misma llave
        fun titleKeyOf(title: String): String {
            val clean = cleanTitle(title)
            val withoutAccents = COMBINING_MARKS.replace(Normalizer.normalize(clean, Normalizer.Form.NFD), "")
            return withoutAccents.lowercase(Locale.ROOT)
        }

        //titulo como se guarda: recortado y con un solo espacio entre palabras
        fun cleanTitle(title: String): String = SPACES.replace(title.trim(), " ")

        fun titleError(title: String): String? {
            val clean = cleanTitle(title)
            return when {
                clean.isEmpty() -> EMPTY_TITLE_ERROR
                clean.length > MAX_TITLE_LENGTH -> "Title cannot exceed $MAX_TITLE_LENGTH characters"
                else -> null
            }
        }

        fun textError(text: String): String? {
            val clean = text.trim()
            return when {
                clean.isEmpty() -> EMPTY_TEXT_ERROR
                clean.length > MAX_TEXT_LENGTH -> "Text cannot exceed $MAX_TEXT_LENGTH characters"
                else -> null
            }
        }

        //el primer error de una galleta nueva, o null si se puede guardar
        fun validationError(title: String, text: String): String? = titleError(title) ?: textError(text)

        //titulo final sin repetir: si ya existe se agrega "-N" con el primer numero libre (estilo finder)
        //si existen "-1" y "-3", la siguiente es "-2"; las comidas tambien cuentan porque siguen existiendo
        //si el titulo con el sufijo pasa de 60, se recorta la base para que quepa
        fun uniqueTitle(title: String, existingKeys: Set<String>): String {
            val base = cleanTitle(title)
            if (titleKeyOf(base) !in existingKeys) return base
            var number = 1
            while (true) {
                val candidate = withSuffix(base, number)
                if (titleKeyOf(candidate) !in existingKeys) return candidate
                number++
            }
        }

        //base + "-N" sin pasar de 60 caracteres
        fun withSuffix(base: String, number: Int): String {
            val suffix = "-$number"
            val room = MAX_TITLE_LENGTH - suffix.length
            val trimmedBase = if (base.length > room) base.take(room).trimEnd() else base
            return trimmedBase + suffix
        }
    }
}

//fila del listado: solo lo que se pinta en la lista, sin el texto completo
data class CookieListItem(
    val id: String,
    val title: String,
    val state: CookieState,
    val hasImage: Boolean,
    val receivedFrom: String?,
    val isSynced: Boolean,
)
