package com.moviles.ark.domain.models

// Herramienta del catálogo (toolbox) tal como la ve la app.
// Clase base de la cual heredan las herramientas específicas del catálogo.
open class Tool(
    open val id: String,
    open val name: String,
    open val description: String,
    // Filtro del tool hub expresado de forma fuertemente tipada con ToolCategory
    open val category: ToolCategory,
    // Forma de interactuar: "voice", "text", "touch" o "photo" (la usa la pregunta de negocio #4)
    open val format: String,
    // Nombre del drawable del icono
    open val iconName: String,
) {
    // Constructor secundario para mantener compatibilidad con codigo/entidades que usan String
    constructor(
        id: String,
        name: String,
        description: String,
        categoryString: String,
        format: String,
        iconName: String
    ) : this(
        id = id,
        name = name,
        description = description,
        category = ToolCategory.fromId(categoryString),
        format = format,
        iconName = iconName
    )

    val categoryId: String get() = category.id

    //true si la app ya tiene pantalla para esta herramienta; las demas siguen en el catalogo de firestore pero no se muestran
    val isImplemented: Boolean get() = id in IMPLEMENTED_TOOL_IDS

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
        return "Tool(id='$id', name='$name', description='$description', category=$category, format='$format', iconName='$iconName')"
    }

    companion object {
        //herramientas con pantalla propia en el NavHost; al implementar una nueva se agrega aqui
        val IMPLEMENTED_TOOL_IDS = setOf("custom_breathing", "scream_tank", "photo_of_the_day")

        //herramienta que se abre cuando no se puede elegir otra (siempre esta en el telefono)
        const val FALLBACK_TOOL_ID = "custom_breathing"

        //catalogo por defecto del tool hub, el mismo de assets/tools.json; lo usan los previews y el home al abrir
        val DEFAULT_CATALOG = listOf(
            Tool("custom_breathing", "Custom breathing", "Build the rhythm that fits you.", "calm_down", "touch", "ic_tool_custom_breathing"),
            Tool("blow_it_out", "Blow it out", "Blow out the tension, one breath at a time.", "release", "voice", "ic_tool_blow_it_out"),
            Tool("scream_tank", "Scream tank", "Let it out. Nobody is listening but you.", "release", "voice", "ic_tool_scream_tank"),
            Tool("body_mapping", "Body mapping", "Mark where the feeling lives.", "reflect", "touch", "ic_tool_body_mapping"),
            Tool("emotion_detective", "Emotion detective", "Not sure what you feel? Let's find out.", "reflect", "text", "ic_tool_emotion_detective"),
            Tool("achievement_jar", "Achievement jar", "Save the good stuff. Read it when you need it.", "celebrate", "text", "ic_tool_achievement_jar"),
            Tool("photo_of_the_day", "Photo of the day", "One photo a day, one new memory.", "celebrate", "photo", "ic_tool_photo_of_the_day")
        )

        //solo las que ya tienen pantalla
        fun implemented(tools: List<Tool>): List<Tool> = tools.filter { it.isImplemented }

        //las de una categoria; null es "todas"
        fun inCategory(tools: List<Tool>, category: ToolCategory?): List<Tool> =
            if (category == null) tools else tools.filter { it.category == category }

        //categorias que tienen alguna herramienta; la elegida se deja para poder quitar el filtro
        fun availableCategories(tools: List<Tool>, selected: ToolCategory?): List<ToolCategory> =
            ToolCategory.entries.filter { category -> category == selected || tools.any { it.category == category } }

        //"Surprise me": una al azar entre las que ya tienen pantalla; null si no hay ninguna
        fun surprisePick(tools: List<Tool>): Tool? = implemented(tools).randomOrNull()
    }
}
