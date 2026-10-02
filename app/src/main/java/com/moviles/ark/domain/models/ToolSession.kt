package com.moviles.ark.domain.models

//una vez que el usuario termino una herramienta
//el repositorio la arma desde la tabla tool_sessions (ToolSessionEntity, #11)
data class ToolSession(
    //id de la herramienta, el mismo de Tool (por ejemplo "custom_breathing")
    val toolId: String,
    //"voice", "text", "touch" o "photo"
    val format: String,
    //cuando empezo, en milisegundos
    val startedAt: Long,
    val durationSeconds: Int
)
