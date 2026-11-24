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
    //en lugar de la imagen que no esta en el telefono: sin red o si fallo la descarga; null si no hace falta
    val imageNotice: String? = null,
    //galleta comida que espera confirmacion para borrarla para siempre
    val pendingDelete: CookieListItem? = null,
    val isOnline: Boolean = true,
    //galletas que faltan por subir a la nube (⏳)
    val pendingUploads: Int = 0,
) {
    val inJarItems: List<CookieListItem> get() = listItems.filter { it.state == com.moviles.ark.domain.models.CookieState.ACTIVE }
    val eatenItems: List<CookieListItem> get() = listItems.filter { it.state == com.moviles.ark.domain.models.CookieState.EATEN }
    val isJarEmpty: Boolean get() = !isLoading && jarCookies.isEmpty()
    //"⏳ N cookies waiting to upload" arriba del jarro; null si todo esta subido
    val pendingLabel: String? get() = if (pendingUploads > 0) com.moviles.ark.domain.models.CookieMessages.waitingToUpload(pendingUploads) else null
}

//lo que hace el boton de un aviso
sealed interface CookieMessageAction {
    //"Undo" despues de comer: la galleta vuelve al jarro
    data class UndoEat(val cookieId: String) : CookieMessageAction
    //"Retry" de una imagen que no se pudo bajar
    data class RetryImage(val cookieId: String) : CookieMessageAction
    //"Retry" de la bajada de galletas de la nube
    data object RetryRefresh : CookieMessageAction

    //acciones de amigos y regalos: las atiende CookieSocialViewModel (y "View" la navegacion)
    sealed interface Social : CookieMessageAction

    //"View" de un regalo nuevo (M27, M28): abre la bandeja
    data object ViewInbox : Social

    //"Retry" de una accion de amigos que fallo por la red (M22)
    data object RetrySocial : Social

    //"Retry" de un regalo que no se envio (M24)
    data class RetryGift(val cookieId: String, val friendUid: String) : Social

    //"Retry" de un regalo que no se alcanzo a aceptar (M31)
    data class RetryAccept(val shareId: String) : Social
}

//aviso de una sola vez (snackbar); action es lo que pasa al tocar su boton
data class CookieMessage(
    val text: String,
    val actionLabel: String? = null,
    val isLong: Boolean = false,
    val action: CookieMessageAction? = null,
    //avisos del estado de la red (M1 y M2): uno nuevo reemplaza al anterior que siga visible
    val isNetworkStatus: Boolean = false,
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
