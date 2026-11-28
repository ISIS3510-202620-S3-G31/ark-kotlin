package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.WishModel

//las tres vistas del farol de deseos
enum class WishLanternView {
    //el cielo: el farol grande al frente y los demas faroles atras
    SKY,
    //donde se escribe o se edita un deseo
    EDITOR,
    //la lista con todos los deseos
    LIST
}

//un farol del fondo del cielo, ya listo para dibujar
//no trae el texto del deseo: el texto se lee solo cuando el usuario toca el farol
data class SkyLantern(
    //id del deseo de este farol
    val wishId: String,
    //lugar en el cielo como fraccion del ancho, de 0 (izquierda) a 1 (derecha)
    val x: Float,
    //lugar en el cielo como fraccion del alto, de 0 (arriba) a 1 (abajo)
    val y: Float,
    //cuanto tarda en subir y bajar flotando, en milisegundos; cada farol tiene su ritmo
    val floatDurationMillis: Int
) {
    //ancho del farol en dp: los de mas abajo estan mas cerca, entonces se ven mas grandes
    //lo usan la pantalla (para dibujarlo) y SkyLanternPlacer (para que no se toquen)
    fun widthDp(): Float {
        return 22f + 18f * y
    }

    //alto del farol en dp; el dibujo del farol es 1.25 veces mas alto que ancho
    fun heightDp(): Float {
        return widthDp() * 1.25f
    }
}

//estado del farol de deseos; lo comparten las tres vistas
data class WishLanternUiState(
    //la vista que se esta mostrando
    val currentView: WishLanternView = WishLanternView.SKY,

    // ---------- cielo ----------
    //faroles del fondo con su posicion ya calculada (maximo 20)
    val skyLanterns: List<SkyLantern> = emptyList(),
    //faroles que acaban de salir del cielo; se dibujan un momento mientras se desvanecen
    val leavingLanterns: List<SkyLantern> = emptyList(),
    //id del farol que se acaba de mandar a volar; brilla un momento para que el usuario vea donde quedo
    val highlightedWishId: String? = null,
    //true hasta que llegan los faroles por primera vez
    val isSkyLoading: Boolean = true,
    //ids de los faroles encendidos (los que estan en la cache)
    val litWishIds: Set<String> = emptySet(),
    //deseo recien guardado que todavia no se ha mandado a volar; null si no hay ninguno
    val wishToRelease: WishModel? = null,
    //deseo que se abrio al tocar un farol del cielo; null si no hay ninguno abierto
    val openedWish: WishModel? = null,

    // ---------- lista ----------
    //todos los deseos del usuario con su texto, del mas nuevo al mas viejo
    //se cargan solo cuando el usuario abre la lista
    val wishes: List<WishModel> = emptyList(),
    //true hasta que llega la lista por primera vez
    val isListLoading: Boolean = true,
    //deseo que el usuario quiere borrar; mientras no sea null se muestra la confirmacion
    val wishToDelete: WishModel? = null,

    // ---------- editor ----------
    //texto que se esta escribiendo en el editor
    val editorText: String = "",
    //id del deseo que se esta editando; null cuando se esta escribiendo uno nuevo
    val editingWishId: String? = null,
    //true mientras se guarda, para no guardar dos veces
    val isSaving: Boolean = false,

    // ---------- general ----------
    //false cuando el telefono no tiene internet
    val isOnline: Boolean = true,
    //true si faltan por traer de firestore los deseos anteriores y no se pudo (sin internet)
    val isRestorePending: Boolean = false,
    //mensaje cuando algo sale mal; null si todo va bien
    val errorMessage: String? = null
) {
    //true si el editor esta cambiando un deseo que ya existia
    fun isEditing(): Boolean {
        return editingWishId != null
    }

    //el boton guardar solo funciona si hay texto y no se esta guardando ya
    fun canSave(): Boolean {
        return editorText.isNotBlank() && !isSaving
    }

    //faroles del fondo que se dibujan: todos menos el que esta esperando a que lo manden a volar
    fun skyLanternsToShow(): List<SkyLantern> {
        val result = mutableListOf<SkyLantern>()
        for (lantern in skyLanterns) {
            if (lantern.wishId != wishToRelease?.id) {
                result.add(lantern)
            }
        }
        return result
    }

    //true si el usuario todavia no tiene ningun deseo
    fun isSkyEmpty(): Boolean {
        return !isSkyLoading && skyLanterns.isEmpty()
    }
}
