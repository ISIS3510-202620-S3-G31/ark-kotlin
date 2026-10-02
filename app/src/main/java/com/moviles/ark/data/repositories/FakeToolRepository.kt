package com.moviles.ark.data.repositories

import com.moviles.ark.domain.models.Tool
import com.moviles.ark.domain.repositories.ToolRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

//repositorio falso con datos de ejemplo, para previews y para avanzar en las pantallas
//mientras llega el repositorio real con room y firestore (#9 + #11 + #20)
class FakeToolRepository(initialTools: List<Tool> = sampleTools) : ToolRepository {
    private val tools = MutableStateFlow(initialTools)

    override fun getTools(): Flow<List<Tool>> = tools

    override suspend fun getToolById(id: String): Tool? = tools.value.find { it.id == id }

    //no hay red que consultar, siempre sale bien
    override suspend fun refreshTools(): Result<Unit> = Result.success(Unit)

    companion object {
        //herramientas del tool hub (frontend-kotlin); formatos e iconos son provisionales
        val sampleTools = listOf(
            Tool("custom_breathing", "Custom breathing", "Build the rhythm that fits you.", "calm_down", "touch", "ic_tool_custom_breathing"),
            Tool("blow_it_out", "Blow it out", "Blow out the tension, one breath at a time.", "release", "voice", "ic_tool_blow_it_out"),
            Tool("scream_tank", "Scream tank", "Let it out. Nobody is listening but you.", "release", "voice", "ic_tool_scream_tank"),
            Tool("body_mapping", "Body mapping", "Mark where the feeling lives.", "reflect", "touch", "ic_tool_body_mapping"),
            Tool("emotion_detective", "Emotion detective", "Not sure what you feel? Let's find out.", "reflect", "text", "ic_tool_emotion_detective"),
            Tool("achievement_jar", "Achievement jar", "Save the good stuff. Read it when you need it.", "celebrate", "text", "ic_tool_achievement_jar"),
            Tool("photo_of_the_day", "Photo of the day", "One photo a day, one new memory.", "celebrate", "photo", "ic_tool_photo_of_the_day")
        )
    }
}
