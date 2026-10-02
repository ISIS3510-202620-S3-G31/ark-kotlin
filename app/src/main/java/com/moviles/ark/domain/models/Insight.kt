package com.moviles.ark.domain.models

//tipo de hallazgo; la tarjeta del #29 elige el icono segun el tipo
enum class InsightType {
    TOOL_MOOD_CORRELATION,
    MOOD_TREND,
    TIME_OF_DAY_PATTERN,
    FREQUENT_EMOTION,
    FAVORITE_TOOL
}

//a donde lleva el boton de la tarjeta; el dominio no conoce las rutas,
//la pantalla traduce cada accion a su ruta del NavHost
sealed interface InsightAction {
    //abrir el check-in de animo
    data object CheckIn : InsightAction

    //abrir la caja de herramientas
    data object Toolbox : InsightAction

    //abrir una herramienta puntual
    data class OpenTool(val toolId: String) : InsightAction
}

//un hallazgo util sobre el usuario, calculado en el telefono con sus propios datos
data class Insight(
    val type: InsightType,
    //frase corta, por ejemplo "Breathing helps you"
    val title: String,
    //lo que se encontro, con numeros
    val message: String,
    //consejo concreto
    val advice: String,
    //texto del boton y a donde lleva
    val actionLabel: String,
    val action: InsightAction
)
