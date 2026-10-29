package com.moviles.ark.domain.models

//avatares que el usuario puede elegir en el perfil; la imagen de cada uno la pone la ui
data class Avatar(
    val id: String,
    val name: String
) {
    companion object {
        const val DEFAULT_ID = "avatar_1"

        val PRESETS: List<Avatar> = listOf(
            Avatar("avatar_1", "Fox"),
            Avatar("avatar_2", "Owl"),
            Avatar("avatar_3", "Spark"),
            Avatar("avatar_4", "Panda"),
            Avatar("avatar_5", "Cat"),
            Avatar("avatar_6", "Sprout")
        )
    }
}
