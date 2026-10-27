package com.moviles.ark.domain.models

import com.moviles.ark.domain.repositories.AnalyticsRepository

//mide cuanto tiempo esta la app en pantalla
//1. cuando la app pasa a primer plano se llama onAppForeground()
//2. mientras sigue en pantalla, cada CHECKPOINT_INTERVAL_MS se llama onCheckpoint() y se envia lo que lleva la sesion
//3. cuando pasa a segundo plano se llama onAppBackground() y se envia el resto
//cada envio manda un pedazo por cada dia que toco; sumando los pedazos de un dia sale su tiempo de pantalla
//vive en el AppContainer (uno solo para toda la app), igual que ToolLatencyTracker
class ScreenTimeTracker(
    private val analyticsRepository: AnalyticsRepository,
    //reloj en milisegundos; se puede cambiar para fijar el tiempo
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() }
) {
    //cuando empezo la sesion actual; null si la app no esta en pantalla
    private var foregroundSince: Long? = null

    fun onAppForeground() {
        if (foregroundSince == null) foregroundSince = currentTimeMillis()
    }

    //la app sigue en pantalla: se envia lo que lleva la sesion y se vuelve a contar desde ahora
    //asi el dashboard ve el tiempo mientras el usuario sigue en la app, no solo cuando sale
    fun onCheckpoint() {
        val start = foregroundSince ?: return
        val now = currentTimeMillis()
        foregroundSince = now
        send(start, now)
    }

    fun onAppBackground() {
        val start = foregroundSince ?: return
        foregroundSince = null
        send(start, currentTimeMillis())
    }

    //el modelo decide si el pedazo es valido y como se reparte entre dias
    private fun send(start: Long, end: Long) {
        for (entry in ScreenTimeSession(start, end).splitByDay()) {
            analyticsRepository.logScreenTime(entry)
        }
    }

    companion object {
        //cada cuanto se envia el tiempo de pantalla mientras la app sigue abierta
        const val CHECKPOINT_INTERVAL_MS = 60_000L
    }
}
