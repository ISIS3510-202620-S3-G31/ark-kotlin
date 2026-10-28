package com.moviles.ark.domain.models

//por donde abrio el usuario una herramienta; viaja en el evento de latencia de la pregunta de negocio #1
//sirve para comparar si alguna entrada es mas lenta que otra (por ejemplo surprise me lee el catalogo antes de abrir)
enum class ToolEntryPoint(val id: String) {
    //tarjeta "Suggested for today" del home
    RECOMMENDATION("recommendation"),
    //tarjeta "Leave it to chance" del home
    SURPRISE_ME("surprise_me"),
    //tarjeta de sugerencia por clima del home
    WEATHER("weather"),
    //lista completa de herramientas (pestana Tools o "Browse full catalog")
    CATALOG("catalog")
}
