package com.moviles.ark.ui.viewmodels

import android.graphics.Bitmap
import com.moviles.ark.domain.models.CookieListItem
import com.moviles.ark.domain.models.CookieModel

//pestanas del listado
enum class CookieListTab { IN_JAR, EATEN }

//estado de la pantalla del jarro, el listado y el detalle
data class CookieJarUiState(
    val isLoading: Boolean = true,
    //las 10 del dia (o menos), en el orden de sus huecos en el jarro
    val jarCookies: List<CookieModel> = emptyList(),
    //listado completo (liviano) para el bottom sheet
    val listItems: List<CookieListItem> = emptyList(),
    val showList: Boolean = false,
    val listTab: CookieListTab = CookieListTab.IN_JAR,
    //galleta abierta en el detalle
    val selectedCookie: CookieModel? = null,
    val selectedImage: Bitmap? = null,
    val isImageLoading: Boolean = false,
    //galleta comida que espera confirmacion para borrarla para siempre
    val pendingDelete: CookieListItem? = null,
    val isOnline: Boolean = true,
) {
    val inJarItems: List<CookieListItem> get() = listItems.filter { it.state == com.moviles.ark.domain.models.CookieState.ACTIVE }
    val eatenItems: List<CookieListItem> get() = listItems.filter { it.state == com.moviles.ark.domain.models.CookieState.EATEN }
    val isJarEmpty: Boolean get() = !isLoading && jarCookies.isEmpty()
}

//aviso de una sola vez (snackbar); undoEatId es la galleta que se devuelve al tocar "Undo"
data class CookieMessage(
    val text: String,
    val actionLabel: String? = null,
    val isLong: Boolean = false,
    val undoEatId: String? = null,
)

//estado del formulario de galleta nueva
data class NewCookieUiState(
    val title: String = "",
    val text: String = "",
    //titulo con el que se guardaria si el escrito ya existe; null si no cambia
    val finalTitle: String? = null,
    //imagen elegida (camara o galeria) y su vista previa
    val imageUri: String? = null,
    val imagePreview: Bitmap? = null,
    //archivo temporal preparado para la camara
    val captureUri: String? = null,
    val titleError: String? = null,
    val textError: String? = null,
    val isSaving: Boolean = false,
)
