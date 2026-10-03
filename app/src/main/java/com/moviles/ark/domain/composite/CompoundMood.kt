package com.moviles.ark.domain.composite

//composite segun gof: almacena y gestiona un grupo de mood components
class CompoundMood(val customName: String? = null) : MoodComponent {
    //lista interna donde se guardan los componentes hijos
    private val children: MutableList<MoodComponent> = mutableListOf()

    //retorna un nombre psicologico segun las emociones combinadas
    override fun getName(): String {
        if (customName != null && customName.isNotBlank()) {
            return customName
        }
        val emotions = getEmotions()
        //reglas de combinaciones emocionales (rueda de plutchik y ekman)
        if (emotions.contains(Emotion.HAPPINESS) && emotions.contains(Emotion.SADNESS)) {
            return "Nostalgia"
        }
        if (emotions.contains(Emotion.ANGER) && emotions.contains(Emotion.DISGUST)) {
            return "Frustration"
        }
        if (emotions.contains(Emotion.FEAR) && emotions.contains(Emotion.SURPRISE)) {
            return "Anxiety"
        }
        if (emotions.contains(Emotion.HAPPINESS) && emotions.contains(Emotion.SURPRISE)) {
            return "Excitement"
        }
        if (emotions.contains(Emotion.ANGER) && emotions.contains(Emotion.FEAR)) {
            return "Stress"
        }
        if (emotions.contains(Emotion.SADNESS) && emotions.contains(Emotion.ANGER)) {
            return "Resentment"
        }
        return "Mixed Mood"
    }

    //agrega un componente hijo a la lista
    override fun add(component: MoodComponent) {
        children.add(component)
    }

    //remueve un componente hijo de la lista
    override fun remove(component: MoodComponent) {
        children.remove(component)
    }

    //obtiene un hijo segun su posicion
    override fun getChild(index: Int): MoodComponent? {
        if (index >= 0 && index < children.size) {
            return children[index]
        }
        return null
    }

    //retorna la lista de hijos
    override fun getChildren(): List<MoodComponent> {
        return children
    }

    //recorrido recursivo de gof: junta las emociones de todos sus componentes
    override fun getEmotions(): List<Emotion> {
        val result = mutableListOf<Emotion>()
        for (child in children) {
            val childEmotions = child.getEmotions()
            result.addAll(childEmotions)
        }
        return result
    }

    //calcula el promedio de intensidad de todos sus componentes
    override fun getIntensity(): Float {
        if (children.isEmpty()) {
            return 0f
        }
        var totalIntensity = 0f //decimal 0
        for (child in children) {
            totalIntensity += child.getIntensity()
        }
        return totalIntensity / children.size
    }
}
