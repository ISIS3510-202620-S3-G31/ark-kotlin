package com.moviles.ark.domain.models

import com.moviles.ark.domain.repositories.AnalyticsRepository

//mide cuanto tiempo esta la app en pantalla
//1. cuando la app pasa a primer plano se llama onAppForeground()
//2. cuando pasa a segundo plano se llama onAppBackground() y se envia un evento por cada dia que toco la sesion
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

    fun onAppBackground() {
        val start = foregroundSince ?: return
        foregroundSince = null
        //el modelo decide si la sesion es valida y como se reparte entre dias
        for (entry in ScreenTimeSession(start, currentTimeMillis()).splitByDay()) {
            analyticsRepository.logScreenTime(entry)
        }
    }
}
