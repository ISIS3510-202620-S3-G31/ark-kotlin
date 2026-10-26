package com.moviles.ark.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.messaging
import com.moviles.ark.ArkApplication
import com.moviles.ark.data.local.ToolCatalogFileStore
import com.moviles.ark.data.remote.dtos.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

//receptor de push (fcm) que avisa que cambio el catalogo de herramientas (#156)
//esta en data/remote porque fcm es otra forma de recibir datos del backend, igual que firestore
//solo se usan mensajes de datos: no se muestra ninguna notificacion, por eso no se pide POST_NOTIFICATIONS
class ToolsCatalogMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data[KEY_TYPE] != TYPE_TOOLS_CATALOG_UPDATED) return
        val toolRepository = (application as ArkApplication).container.toolRepository

        //fcm llama este metodo en un hilo de fondo y da pocos segundos antes de poder cerrar el servicio,
        //por eso se espera aqui (runBlocking) a que termine en vez de lanzar una corrutina que podria quedar a medias
        runBlocking(Dispatchers.IO) {
            //si el push trae la lista completa se guarda sin ir a la red; si no trae o esta danada se baja de firestore
            val pushedTools = data[KEY_TOOLS]?.let { json ->
                runCatching { ToolCatalogFileStore.parseTools(json).map { it.toDomain() } }.getOrNull()
            }
            val result = withTimeoutOrNull(UPDATE_TIMEOUT_MILLIS) {
                if (pushedTools != null) toolRepository.saveCatalog(pushedTools) else toolRepository.refreshTools()
            }
            Log.d(TAG, "Tools catalog update from push: ${result ?: "timeout"}")
        }
    }

    //el token cambia al reinstalar o borrar datos; se vuelve a suscribir al topic por si acaso
    override fun onNewToken(token: String) {
        subscribeToCatalogUpdates()
    }

    companion object {
        private const val TAG = "ToolsCatalogMessaging"
        const val TOOLS_CATALOG_TOPIC = "tools_catalog"
        const val KEY_TYPE = "type"
        const val KEY_TOOLS = "tools"
        const val TYPE_TOOLS_CATALOG_UPDATED = "tools_catalog_updated"
        private const val UPDATE_TIMEOUT_MILLIS = 15_000L

        //todos los telefonos escuchan el mismo topic; suscribirse otra vez no hace nada si ya estaba suscrito
        fun subscribeToCatalogUpdates() {
            Firebase.messaging.subscribeToTopic(TOOLS_CATALOG_TOPIC)
                .addOnFailureListener { error ->
                    CrashlyticsHelper.logNonFatal(
                        componentName = "ToolsCatalogMessagingService",
                        action = "subscribeToTopic",
                        throwable = error,
                        extraKeys = mapOf("topic" to TOOLS_CATALOG_TOPIC)
                    )
                }
        }
    }
}
