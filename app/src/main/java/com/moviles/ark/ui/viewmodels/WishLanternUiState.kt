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

//estado del farol de deseos; lo comparten las tres vistas
data class WishLanternUiState(
    //la vista que se esta mostrando
    val currentView: WishLanternView = WishLanternView.SKY,

    //todos los deseos del usuario, del mas nuevo al mas viejo
    val wishes: List<WishModel> = emptyList(),
    //ids de los faroles encendidos (los que estan en la cache)
    val litWishIds: Set<String> = emptySet(),
    //true hasta que llegan los deseos por primera vez
    val isLoading: Boolean = true,

    //deseo recien guardado que todavia no se ha mandado a volar; null si no hay ninguno
    val wishToRelease: WishModel? = null,
    //deseo que se abrio al tocar un farol del cielo; null si no hay ninguno abierto
    val openedWish: WishModel? = null,

    //texto que se esta escribiendo en el editor
    val editorText: String = "",
    //id del deseo que se esta editando; null cuando se esta escribiendo uno nuevo
    val editingWishId: String? = null,
    //true mientras se guarda, para no guardar dos veces
    val isSaving: Boolean = false,

    //deseo que el usuario quiere borrar; mientras no sea null se muestra la confirmacion
    val wishToDelete: WishModel? = null,

    //false cuando el telefono no tiene internet
    val isOnline: Boolean = true,
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

    //faroles del fondo del cielo: todos menos el que esta esperando a que lo manden a volar
    fun skyWishes(): List<WishModel> {
        val result = mutableListOf<WishModel>()
        for (wish in wishes) {
            if (wish.id != wishToRelease?.id) {
                result.add(wish)
            }
        }
        return result
    }
}
