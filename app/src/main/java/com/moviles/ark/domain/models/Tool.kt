package com.moviles.ark.domain.models

// Herramienta del catálogo (toolbox) tal como la ve la app.
// Clase base de la cual heredan las herramientas específicas del catálogo.
open class Tool(
    open val id: String,
    open val name: String,
    open val description: String,
    // Filtro del tool hub: "calm_down", "release", "reflect" o "celebrate"
    open val category: String,
    // Forma de interactuar: "voice", "text", "touch" o "photo" (la usa la bq del #34)
    open val format: String,
    // Nombre del drawable del icono
    open val iconName: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Tool) return false
        return (id == other.id) &&
                (name == other.name) &&
                (description == other.description) &&
                (category == other.category) &&
                (format == other.format) &&
                (iconName == other.iconName)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + iconName.hashCode()
        return result
    }

    override fun toString(): String {
        return "Tool(id='$id', name='$name', description='$description', category='$category', format='$format', iconName='$iconName')"
    }
}
