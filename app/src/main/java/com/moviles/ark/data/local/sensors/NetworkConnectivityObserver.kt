package com.moviles.ark.data.local.sensors

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.ConcurrentHashMap

//observador reactivo que monitorea en tiempo real si el celular tiene conexion a internet
class NetworkConnectivityObserver(
    context: Context
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    //emite un flujo con true cuando hay conexion a internet y false cuando se pierde
    //android llama estos avisos en su propio hilo; trySend los pasa al flow y el que lo recoge los recibe en su hilo
    fun observe(): Flow<Boolean> = callbackFlow {
        //estado de cada red segun lo que dicen los propios avisos: true si ya esta validada (tiene internet de verdad)
        //no se le pregunta a connectivityManager.activeNetwork dentro de un aviso: android puede avisar antes de
        //actualizarla, y al perder la ultima red (modo avion) devolveria la red vieja como validada; el flow se quedaria
        //en "online" y ningun aviso posterior lo corregiria, porque ya no queda ninguna red que avise
        val networks = ConcurrentHashMap<Network, Boolean>()

        //hay internet si alguna red sigue validada: perder el wifi no deja sin internet si quedan los datos
        fun publish(reason: String) {
            var online = false
            for (isValidated in networks.values) {
                if (isValidated) online = true
            }
            Log.d(TAG, "$reason -> online=$online (${networks.size} networks)")
            trySend(online)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                //desde android 8 siempre llega onCapabilitiesChanged justo despues con sus capacidades
                //en 7 se consultan las de esta red (no las de la red activa)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    networks[network] = capabilities != null && isValidated(capabilities)
                    publish("available")
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                networks[network] = isValidated(networkCapabilities)
                publish("capabilities changed")
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                networks.remove(network)
                publish("lost")
            }

            override fun onUnavailable() {
                super.onUnavailable()
                networks.clear()
                trySend(false)
            }
        }

        //emitimos de inmediato el estado actual de la red (fuera de un aviso la consulta directa si es confiable)
        trySend(isConnected())

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    //consulta directa e inmediata del estado de la conexion
    fun isConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return isValidated(capabilities)
    }

    //validada = android ya comprobo que esa red si llega a internet
    private fun isValidated(capabilities: NetworkCapabilities): Boolean {
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isChecked = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return hasInternet && isChecked
    }

    //detecta si el modo avion esta activado
    fun isAirplaneModeOn(): Boolean {
        return try {
            Settings.Global.getInt(
                appContext.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) != 0
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "NetworkObserver"
    }
}
