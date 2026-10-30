package com.moviles.ark.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moviles.ark.data.local.daos.BreathingSessionDao
import com.moviles.ark.data.local.daos.EmotionCheckInDao
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.daos.PhotoEntryDao
import com.moviles.ark.data.local.daos.StatsDailyVisitDao
import com.moviles.ark.data.local.daos.ToolDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.daos.UserDao
import com.moviles.ark.data.local.entities.BreathingSessionEntity
import com.moviles.ark.data.local.entities.CheckInEntity
import com.moviles.ark.data.local.entities.FeedbackEntity
import com.moviles.ark.data.local.entities.PhotoEntryEntity
import com.moviles.ark.data.local.entities.StatsDailyVisitEntity
import com.moviles.ark.data.local.entities.ToolEntity
import com.moviles.ark.data.local.entities.ToolInteractionEntity
import com.moviles.ark.data.local.entities.UserEntity

//base de datos local de la app: aqui se registran todas las tablas (entities) y sus daos
//reemplaza a AppDatabase.kt, que se borra (nadie la usaba)
//version 2: se agrego breathing_sessions; cada vez que se cambie una tabla se sube el numero
//version 3: se agrego users, el perfil guardado en el telefono
//version 7: se agrego stats_daily_visits, conteo de visitas diarias a estadisticas
//version 8: emociones del feedback y ciudad del check-in, con migracion para no borrar lo que no se ha subido
//version 9: cuenta del feedback pendiente, para no subirlo a otra cuenta si se cierra sesion sin red
//(sin migracion para un salto de version, room borra y crea de nuevo la base local)
@Database(
    entities = [
        ToolEntity::class,
        CheckInEntity::class,
        ToolInteractionEntity::class,
        FeedbackEntity::class,
        PhotoEntryEntity::class,
        BreathingSessionEntity::class,
        UserEntity::class,
        StatsDailyVisitEntity::class,
    ],
    version = 9,
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
    abstract fun statsDailyVisitDao(): StatsDailyVisitDao

    companion object {
        @Volatile
        private var INSTANCE: ArkDatabase? = null

        //solo agrega columnas: los check-ins y feedbacks pendientes de subir se conservan
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tool_feedback ADD COLUMN emotions TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE mood_checkins ADD COLUMN cityName TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tool_feedback ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            }
        }

        //singleton con doble comprobacion para que toda la app comparta un unico pool de conexiones
        fun getInstance(context: Context): ArkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArkDatabase::class.java,
                    "ark.db"
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
