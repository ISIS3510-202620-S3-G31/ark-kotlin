package com.moviles.ark.domain.models

//formatos de interaccion en los que se clasifican las herramientas de ark (#34)
enum class ToolFormat(val id: String, val displayName: String) {
    VOICE("voice", "Voice / Audio"),
    TEXT("text", "Text / Journaling"),
    TOUCH("touch", "Quick Tap / Touch"),
    MULTIMEDIA("multimedia", "Photo / Multimedia");

    companion object {
        fun fromId(id: String): ToolFormat {
            val normalized = id.trim().lowercase()
            return entries.find { it.id == normalized || it.name.equals(normalized, ignoreCase = true) }
                ?: when (normalized) {
                    "photo" -> MULTIMEDIA
                    "audio", "sound" -> VOICE
                    "tap" -> TOUCH
                    else -> TOUCH
                }
        }
    }
}
