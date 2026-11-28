package com.moviles.ark.domain.models

//un deseo del farol de deseos (wish lantern)
data class WishModel(
    //id unico creado en el telefono (uuid)
    val id: String,
    //el texto del deseo
    val text: String,
    //cuando se escribio, en milisegundos
    val createdAt: Long,
    //la ultima vez que se edito, en milisegundos
    val updatedAt: Long
) {
    companion object {
        //id de la herramienta en el catalogo (ToolModel.id)
        const val TOOL_ID = "wish_lantern"

        //la herramienta se usa escribiendo
        const val INTERACTION_TYPE = "text"

        //maximo de letras de un deseo, para que quepa en el farol
        const val MAX_TEXT_LENGTH = 150

        //maximo de faroles encendidos al mismo tiempo (tamano del lru)
        const val MAX_LIT_LANTERNS = 8

        //maximo de faroles que se ven en el fondo del cielo (encendidos + apagados)
        //con mas, el cielo se llena demasiado y no caben sin tocarse
        const val MAX_SKY_LANTERNS = 20
    }
}
