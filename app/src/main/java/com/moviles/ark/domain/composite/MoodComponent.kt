package com.moviles.ark.domain.composite

//component: interfaz base para emociones simples y compuestas
interface MoodComponent {
    //operaciones principales
    fun getName(): String
    fun getEmotions(): List<Emotion>
    fun getIntensity(): Float

    //revisa si una emocion especifica esta presente
    fun contains(emotion: Emotion): Boolean {
        return getEmotions().contains(emotion)
    }

    //gestion de hijos segun gof (las hojas no permiten agregar hijos por defecto)
    fun add(component: MoodComponent) {
        throw UnsupportedOperationException("Cannot add child to a single emotion")
    }

    fun remove(component: MoodComponent) {
        throw UnsupportedOperationException("Cannot remove child from a single emotion")
    }

    fun getChild(index: Int): MoodComponent? {
        return null
    }

    fun getChildren(): List<MoodComponent> {
        return emptyList()
    }
}