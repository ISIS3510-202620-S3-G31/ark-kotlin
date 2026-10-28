package com.moviles.ark.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.moviles.ark.data.local.daos.BreathingSessionDao
import com.moviles.ark.data.local.daos.EmotionCheckInDao
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.daos.PhotoEntryDao
import com.moviles.ark.data.local.daos.ToolDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.daos.UserDao
import com.moviles.ark.data.local.entities.BreathingSessionEntity
import com.moviles.ark.data.local.entities.CheckInEntity
import com.moviles.ark.data.local.entities.FeedbackEntity
import com.moviles.ark.data.local.entities.PhotoEntryEntity
import com.moviles.ark.data.local.entities.ToolEntity
import com.moviles.ark.data.local.entities.ToolInteractionEntity
import com.moviles.ark.data.local.entities.UserEntity

//base de datos local de la app: aqui se registran todas las tablas (entities) y sus daos
//reemplaza a AppDatabase.kt, que se borra (nadie la usaba)
//version 2: se agrego breathing_sessions; cada vez que se cambie una tabla se sube el numero
//version 3: se agrego users, el perfil guardado en el telefono
//(el AppContainer borra y crea de nuevo la base local cuando cambia la version)
@Database(
    entities = [
        ToolEntity::class,
        CheckInEntity::class,
        ToolInteractionEntity::class,
        FeedbackEntity::class,
        PhotoEntryEntity::class,
        BreathingSessionEntity::class,
        UserEntity::class,
    ],
    version = 6,
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
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: ArkDatabase? = null

        //singleton con doble comprobacion para que toda la app comparta un unico pool de conexiones
        fun getInstance(context: Context): ArkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArkDatabase::class.java,
                    "ark.db"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
