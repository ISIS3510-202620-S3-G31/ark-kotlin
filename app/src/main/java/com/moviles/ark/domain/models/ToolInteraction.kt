package com.moviles.ark.domain.models

import java.util.Date

//una vez que el usuario uso una herramienta (una interaccion con ella)
//los atributos siguen la clase ToolInteraction del diagrama de clases
//el repositorio la arma desde la tabla tool_interactions (ToolInteractionEntity, #11)
data class ToolInteraction(
    //usuario que uso la herramienta (User.id)
    val userId: String,
    //herramienta usada (Tool.id), por ejemplo "custom_breathing"
    val toolId: String,
    //cuanto duro, en segundos
    val duration: Int,
    //como interactuo: "voice", "text", "touch" o "photo"
    val interactionType: String,
    //intensidad que registro la herramienta, por ejemplo el volumen del grito; 0 si no aplica
    val intensity: Int = 0,
    //true si el usuario termino la herramienta
    val complete: Boolean = true,
    //resultado numerico de la herramienta, por ejemplo cuantos ciclos de respiracion; 0 si no aplica
    val value: Int = 0,
    //cuando ocurrio
    val timestamp: Date,
    //detalle opcional
    val description: String = ""
)
