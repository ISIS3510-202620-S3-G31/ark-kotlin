package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.moviles.ark.data.local.entities.ToolEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolDao {

    @Query("SELECT * FROM tools")
    fun getTools(): Flow<List<ToolEntity>>

    @Query("SELECT * FROM tools WHERE id = :id")
    suspend fun getToolById(id: String): ToolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTools(tools: List<ToolEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTool(tool: ToolEntity)

    @Query("DELETE FROM tools")
    suspend fun clearTools()

    @Transaction
    suspend fun replaceTools(tools: List<ToolEntity>) {
        clearTools()
        insertTools(tools)
    }
}
