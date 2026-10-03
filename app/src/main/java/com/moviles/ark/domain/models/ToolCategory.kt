package com.moviles.ark.domain.models

/**
 * Strongly typed categories for tools in the Ark Toolbox / Tool Hub.
 */
enum class ToolCategory(val id: String, val displayName: String) {
    SOMATIC_RELEASE("somatic_release", "Somatic Release"),
    BREATHING_REGULATION("breathing_regulation", "Breathing Regulation"),
    EMOTIONAL_AWARENESS("emotional_awareness", "Emotional Awareness"),
    GROUNDING_MINDFULNESS("grounding_mindfulness", "Grounding & Mindfulness"),
    BEHAVIORAL_ACTIVATION("behavioral_activation", "Behavioral Activation");

    companion object {
        /**
         * Safely converts category ID strings to ToolCategory enum instances,
         * with fallback support for legacy category string aliases.
         */
        fun fromId(id: String): ToolCategory {
            val normalized = id.trim().lowercase()
            return entries.find { it.id == normalized || it.name.equals(normalized, ignoreCase = true) }
                ?: when (normalized) {
                    "release" -> SOMATIC_RELEASE
                    "calm_down" -> BREATHING_REGULATION
                    "reflect" -> EMOTIONAL_AWARENESS
                    "celebrate" -> BEHAVIORAL_ACTIVATION
                    "touch", "text", "voice", "photo" -> GROUNDING_MINDFULNESS
                    else -> BREATHING_REGULATION
                }
        }
    }
}
