package com.moviles.ark.domain.models

//cuanto tardo una herramienta en abrirse: desde el toque del usuario hasta que su pantalla se dibujo (#8)
//responde la pregunta de negocio #1 con el evento tool_load_latency
data class ToolLoadLatency(
    //id de la herramienta, el mismo de Tool (por ejemplo "photo_of_the_day")
    val toolId: String,
    val latencyMs: Long
) {
    //reglas de negocio: una medicion negativa o de mas de un minuto no es una carga real
    //(por ejemplo, el usuario toco la herramienta, salio de la app y volvio mucho despues)
    fun isValid(): Boolean {
        return toolId.isNotBlank() && latencyMs in 0..MAX_LATENCY_MS
    }

    companion object {
        const val MAX_LATENCY_MS = 60_000L

        //arma la medicion a partir de dos instantes en nanosegundos (System.nanoTime)
        fun between(toolId: String, startNanos: Long, endNanos: Long): ToolLoadLatency {
            return ToolLoadLatency(toolId, (endNanos - startNanos) / 1_000_000)
        }
    }
}
