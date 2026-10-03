package com.moviles.ark.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.moviles.ark.data.local.daos.BreathingSessionDao
import com.moviles.ark.data.local.daos.EmotionCheckInDao
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.daos.PhotoEntryDao
import com.moviles.ark.data.local.daos.ToolDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.BreathingSessionEntity
import com.moviles.ark.data.local.entities.CheckInEntity
import com.moviles.ark.data.local.entities.FeedbackEntity
import com.moviles.ark.data.local.entities.PhotoEntryEntity
import com.moviles.ark.data.local.entities.ToolEntity
import com.moviles.ark.data.local.entities.ToolInteractionEntity

//base de datos local de la app (#11): aqui se registran todas las tablas (entities) y sus daos
//reemplaza a AppDatabase.kt, que se borra (nadie la usaba)
//version 2: se agrego breathing_sessions (#82); cada vez que se cambie una tabla se sube el numero
//(el AppContainer borra y crea de nuevo la base local cuando cambia la version)
@Database(
    entities = [
        ToolEntity::class,
        CheckInEntity::class,
        ToolInteractionEntity::class,
        FeedbackEntity::class,
        PhotoEntryEntity::class,
        BreathingSessionEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class ArkDatabase : RoomDatabase() {
    abstract fun toolDao(): ToolDao
    abstract fun emotionCheckInDao(): EmotionCheckInDao
    abstract fun toolRecordDao(): ToolRecordDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun photoEntryDao(): PhotoEntryDao
    abstract fun breathingSessionDao(): BreathingSessionDao
}
