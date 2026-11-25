package com.moviles.ark.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moviles.ark.data.local.daos.BreathingSessionDao
import com.moviles.ark.data.local.daos.CookieDao
import com.moviles.ark.data.local.daos.EmotionCheckInDao
import com.moviles.ark.data.local.daos.FavoriteFortuneCookieDao
import com.moviles.ark.data.local.daos.SavedFortuneCardDao
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.daos.PhotoEntryDao
import com.moviles.ark.data.local.daos.StatsDailyVisitDao
import com.moviles.ark.data.local.daos.ToolDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.daos.UserDao
import com.moviles.ark.data.local.daos.WishDao
import com.moviles.ark.data.local.entities.BreathingSessionEntity
import com.moviles.ark.data.local.entities.CheckInEntity
import com.moviles.ark.data.local.entities.CookieEntity
import com.moviles.ark.data.local.entities.FavoriteFortuneCookieEntity
import com.moviles.ark.data.local.entities.SavedFortuneCardEntity
import com.moviles.ark.data.local.entities.FeedbackEntity
import com.moviles.ark.data.local.entities.PhotoEntryEntity
import com.moviles.ark.data.local.entities.StatsDailyVisitEntity
import com.moviles.ark.data.local.entities.ToolEntity
import com.moviles.ark.data.local.entities.ToolInteractionEntity
import com.moviles.ark.data.local.entities.UserEntity
import com.moviles.ark.data.local.entities.WishEntity

//base de datos local de la app: aqui se registran todas las tablas (entities) y sus daos
//reemplaza a AppDatabase.kt, que se borra (nadie la usaba)
//version 2: se agrego breathing_sessions; cada vez que se cambie una tabla se sube el numero
//version 3: se agrego users, el perfil guardado en el telefono
//version 7: se agrego stats_daily_visits, conteo de visitas diarias a estadisticas
//version 8: emociones del feedback y ciudad del check-in, con migracion para no borrar lo que no se ha subido
//version 9: cuenta del feedback pendiente, para no subirlo a otra cuenta si se cierra sesion sin red
//version 12: se agrego cookies, las galletas del cookie jar, con migracion para no perder nada de lo anterior
//version 13: se agrego favorite_fortunes, frases favoritas de la galleta de la fortuna
//version 14: se agrego saved_fortune_cards, trazabilidad de tarjetas guardadas en galeria y nube
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
        CookieEntity::class,
        FavoriteFortuneCookieEntity::class,
        SavedFortuneCardEntity::class,
        WishEntity::class,
    ],
    version = 14,
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
    abstract fun cookieDao(): CookieDao
    abstract fun wishDao(): WishDao
    abstract fun favoriteFortuneCookieDao(): FavoriteFortuneCookieDao
    abstract fun savedFortuneCardDao(): SavedFortuneCardDao

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

        //foto de perfil sincronizada: avatar elegido, si se muestra la foto, su url en storage y si falta subirla
        //quien ya tenia foto la sigue viendo (antes la foto siempre ganaba sobre el avatar)
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN avatarId TEXT NOT NULL DEFAULT 'avatar_1'")
                db.execSQL("ALTER TABLE users ADD COLUMN usesPhoto INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN photoUrl TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN isAvatarSynced INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE users SET usesPhoto = 1 WHERE avatarPath IS NOT NULL")
            }
        }

        //hora de la ultima edicion del perfil y de la foto en este telefono, y la version de la nube que vio por ultima vez,
        //para no pisar al subir un cambio hecho en otro telefono (sin version vista se sube como antes)
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN profileEditedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN avatarEditedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN profileBase TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN avatarBase TEXT")
            }
        }

        //solo crea la tabla nueva de cookie jar y su indice unico; las demas tablas no se tocan
        //el sql es el mismo que genera room para CookieEntity, si no coincide room no abre la base
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cookies` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`titleKey` TEXT NOT NULL, `text` TEXT NOT NULL, `state` TEXT NOT NULL, `hasImage` INTEGER NOT NULL, " +
                        "`localImagePath` TEXT, `remoteImageUrl` TEXT, `lastOpenedAt` INTEGER NOT NULL, `receivedFrom` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cookies_userId_titleKey` ON `cookies` (`userId`, `titleKey`)")
            }
        }

        //agrega la tabla de frases favoritas de la galleta de la fortuna
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorite_fortune_cookies` (`id` INTEGER NOT NULL, `quote` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, `luckyNumber` INTEGER NOT NULL, `savedAt` INTEGER NOT NULL, " +
                        "`isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        //agrega la tabla de tarjetas de la fortuna guardadas en galeria y nube y la tabla de deseos
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_fortune_cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`cookieId` INTEGER NOT NULL, `userId` TEXT NOT NULL, `quote` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, `luckyNumber` INTEGER NOT NULL, `localImageUri` TEXT NOT NULL, " +
                        "`remoteImageUrl` TEXT, `savedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wishes` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, " +
                        "`isDeleted` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
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
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
