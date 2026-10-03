package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.moviles.ark.data.local.entities.ToolEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

@Dao
interface ToolDao {

    @Query("SELECT * FROM tools")
    fun getToolsRaw(): Flow<List<ToolEntity>>

    fun getTools(): Flow<List<ToolEntity>> {
        return getToolsRaw().catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolDao",
                action = "getTools",
                throwable = e
            )
            throw e
        }
    }

    @Query("SELECT * FROM tools WHERE id = :id")
    suspend fun getToolByIdRaw(id: String): ToolEntity?

    suspend fun getToolById(id: String): ToolEntity? {
        return try {
            getToolByIdRaw(id)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolDao",
                action = "getToolById",
                throwable = e,
                extraKeys = mapOf("tool_id" to id)
            )
            null
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToolsRaw(tools: List<ToolEntity>)

    suspend fun insertTools(tools: List<ToolEntity>) {
        try {
            insertToolsRaw(tools)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolDao",
                action = "insertTools",
                throwable = e,
                extraKeys = mapOf("tools_count" to tools.size)
            )
            throw e
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToolRaw(tool: ToolEntity)

    suspend fun insertTool(tool: ToolEntity) {
        try {
            insertToolRaw(tool)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolDao",
                action = "insertTool",
                throwable = e,
                extraKeys = mapOf("tool_id" to tool.id)
            )
            throw e
        }
    }

    @Query("DELETE FROM tools")
    suspend fun clearToolsRaw()

    suspend fun clearTools() {
        try {
            clearToolsRaw()
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolDao",
                action = "clearTools",
                throwable = e
            )
            throw e
        }
    }

    @Transaction
    suspend fun replaceTools(tools: List<ToolEntity>) {
        try {
            clearTools()
            insertTools(tools)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolDao",
                action = "replaceTools",
                throwable = e,
                extraKeys = mapOf("tools_count" to tools.size)
            )
            throw e
        }
    }
}

