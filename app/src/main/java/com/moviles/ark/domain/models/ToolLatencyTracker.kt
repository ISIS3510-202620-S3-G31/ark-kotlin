package com.moviles.ark.domain.models

import com.moviles.ark.domain.repositories.AnalyticsRepository

//mide cuanto tarda en abrirse una herramienta (#8)
//1. al tocar la herramienta se llama start(toolId)
//2. cuando su pantalla termina de dibujarse se llama onToolRendered(toolId)
//vive en el AppContainer (uno solo para toda la app), porque el toque y la pantalla estan en lugares distintos
class ToolLatencyTracker(
    private val analyticsRepository: AnalyticsRepository,
    //reloj monotono en nanosegundos; se puede cambiar para fijar el tiempo
    private val nanoTime: () -> Long = { System.nanoTime() }
) {
    //herramienta que se toco y cuando; solo se espera una a la vez
    private var pendingToolId: String? = null
    private var pendingStartNanos: Long = 0L

    //el usuario toco una herramienta: si ya habia otra esperando, se reemplaza
    fun start(toolId: String) {
        pendingToolId = toolId
        pendingStartNanos = nanoTime()
    }

    //la pantalla de la herramienta termino su primer dibujo
    //si no hubo toque antes (por ejemplo al rotar el telefono), no se mide nada
    fun onToolRendered(toolId: String) {
        if (pendingToolId != toolId) return
        val latency = ToolLoadLatency.between(toolId, pendingStartNanos, nanoTime())
        pendingToolId = null
        //le preguntamos al modelo si la medicion es valida antes de enviarla
        if (latency.isValid()) {
            analyticsRepository.logToolLoadLatency(latency)
        }
    }
}
