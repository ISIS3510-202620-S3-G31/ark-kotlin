package com.moviles.ark.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.moviles.ark.data.local.daos.ToolDao
import com.moviles.ark.data.local.entities.ToolEntity

@Database(
    entities = [ToolEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun toolDao(): ToolDao
}
