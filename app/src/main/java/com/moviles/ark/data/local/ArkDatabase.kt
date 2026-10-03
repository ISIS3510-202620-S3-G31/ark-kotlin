package com.moviles.ark.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.moviles.ark.data.local.daos.EmotionCheckInDao
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.daos.PhotoEntryDao
import com.moviles.ark.data.local.daos.ToolDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.CheckInEntity
import com.moviles.ark.data.local.entities.FeedbackEntity
import com.moviles.ark.data.local.entities.PhotoEntryEntity
import com.moviles.ark.data.local.entities.ToolEntity
import com.moviles.ark.data.local.entities.ToolInteractionEntity

//base de datos local de la app (#11): aqui se registran todas las tablas (entities) y sus daos
//reemplaza a AppDatabase.kt, que se borra (nadie la usaba)
//version 1 porque todavia nadie ha creado la base en un telefono; si se cambia una tabla despues, se sube el numero
@Database(
    entities = [
        ToolEntity::class,
        CheckInEntity::class,
        ToolInteractionEntity::class,
        FeedbackEntity::class,
        PhotoEntryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class ArkDatabase : RoomDatabase() {
    abstract fun toolDao(): ToolDao
    abstract fun emotionCheckInDao(): EmotionCheckInDao
    abstract fun toolRecordDao(): ToolRecordDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun photoEntryDao(): PhotoEntryDao
}
