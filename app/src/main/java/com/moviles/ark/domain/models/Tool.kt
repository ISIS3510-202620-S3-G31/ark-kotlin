package com.moviles.ark.domain.models

//herramienta del catalogo (toolbox) tal como la ve la app
//es distinta de ToolEntity (#20): la entidad es la tabla de room, este modelo no sabe nada de room
data class Tool(
    val id: String,
    val name: String,
    val description: String,
    //filtro del tool hub: "calm_down", "release", "reflect" o "celebrate"
    val category: String,
    //forma de interactuar: "voice", "text", "touch" o "photo" (la usa la bq del #34)
    val format: String,
    //nombre del drawable del icono
    val iconName: String
)
