package com.moviles.ark.data.local

import android.content.Context
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.ToolDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

//catalogo de herramientas guardado como archivo json en el almacenamiento interno de la app (#145)
//filesDir/tools.json es la copia permanente para usar la app sin internet;
//si falta o esta danado se usa el respaldo que viene dentro del apk (assets/tools.json) y se restaura la copia interna
class ToolCatalogFileStore(private val context: Context) {

    private val catalogFile: File get() = File(context.filesDir, FILE_NAME)

    //lee el catalogo del archivo interno y, si no sirve, el de assets
    suspend fun readCatalog(): List<ToolDto> = withContext(Dispatchers.IO) {
        //si no se puede restaurar la copia interna igual se devuelve el respaldo
        readInternalCatalog() ?: readBundledCatalog().also { bundled -> runCatching { writeCatalog(bundled) } }
    }

    //sobrescribe filesDir/tools.json; primero en un temporal y luego se renombra para no dejar el archivo a medias
    suspend fun writeCatalog(tools: List<ToolDto>) = withContext(Dispatchers.IO) {
        val tempFile = File(context.filesDir, "$FILE_NAME.tmp")
        tempFile.writeText(toJson(tools))
        if (!tempFile.renameTo(catalogFile)) {
            tempFile.delete()
            error("Could not save $FILE_NAME")
        }
    }

    //null si el archivo no existe, esta corrupto o no trae herramientas
    private fun readInternalCatalog(): List<ToolDto>? {
        val file = catalogFile
        if (!file.exists()) return null
        return runCatching { parseTools(file.readText()) }
            .onFailure { error ->
                CrashlyticsHelper.logNonFatal(
                    componentName = "ToolCatalogFileStore",
                    action = "readInternalCatalog",
                    throwable = error
                )
            }
            .getOrNull()
    }

    private fun readBundledCatalog(): List<ToolDto> {
        val json = context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }
        return parseTools(json)
    }

    companion object {
        const val FILE_NAME = "tools.json"

        //funciones puras (sin context) para poder probar el parseo sin el telefono
        //el json es un arreglo con los mismos campos de ToolDto; lanza excepcion si esta corrupto o vacio
        fun parseTools(json: String): List<ToolDto> {
            val array = JSONArray(json)
            val tools = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                ToolDto(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    description = item.optString("description"),
                    category = item.optString("category"),
                    format = item.optString("format"),
                    iconName = item.optString("iconName")
                )
            }.filter { it.id.isNotBlank() }
            if (tools.isEmpty()) error("The tools catalog is empty")
            return tools
        }

        fun toJson(tools: List<ToolDto>): String {
            val array = JSONArray()
            tools.forEach { tool ->
                array.put(
                    JSONObject()
                        .put("id", tool.id)
                        .put("name", tool.name)
                        .put("description", tool.description)
                        .put("category", tool.category)
                        .put("format", tool.format)
                        .put("iconName", tool.iconName)
                )
            }
            return array.toString()
        }
    }
}
