package com.moviles.ark.data

import android.content.Context
import androidx.room.Room
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.moviles.ark.data.local.ArkDatabase
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.sensors.Location
import com.moviles.ark.data.repositories.AnalyticsRepositoryImpl
import com.moviles.ark.data.repositories.AuthRepositoryImpl
import com.moviles.ark.data.repositories.FakePhotoRepository
import com.moviles.ark.data.repositories.FakeToolRepository
import com.moviles.ark.data.repositories.LocationRepositoryImpl
import com.moviles.ark.data.repositories.MoodRepositoryImpl
import com.moviles.ark.data.repositories.StatsRepositoryImpl
import com.moviles.ark.domain.models.ToolLatencyTracker
import com.moviles.ark.domain.repositories.AnalyticsRepository
import com.moviles.ark.domain.repositories.AuthRepository
import com.moviles.ark.domain.repositories.LocationRepository
import com.moviles.ark.domain.repositories.MoodRepository
import com.moviles.ark.domain.repositories.PhotoRepository
import com.moviles.ark.domain.repositories.StatsRepository
import com.moviles.ark.domain.repositories.ToolRepository

interface AppContainer {
    val auth: FirebaseAuth
    val firestore: FirebaseFirestore
    val authRepository: AuthRepository
    val locationRepository: LocationRepository
    val moodRepository: MoodRepository
    val toolRepository: ToolRepository
    val statsRepository: StatsRepository
    val photoRepository: PhotoRepository
    val analyticsRepository: AnalyticsRepository
    val toolLatencyTracker: ToolLatencyTracker
    val feedbackDao: FeedbackDao
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    //base de datos room del telefono (#11); private para que los viewmodels siempre pasen por un repositorio
    //lazy: no se crea hasta que un repositorio la use (por ejemplo los de #9, #21 y #26)
    private val database: ArkDatabase by lazy {
        Room.databaseBuilder(context, ArkDatabase::class.java, "ark.db")
            //mientras desarrollamos: si cambia una tabla se borra la base y se crea otra (se pierden los datos locales)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    override val auth: FirebaseAuth by lazy { Firebase.auth }
    override val firestore: FirebaseFirestore by lazy { Firebase.firestore }
    override val authRepository: AuthRepository by lazy { AuthRepositoryImpl(auth, firestore) }
    override val locationRepository: LocationRepository by lazy { LocationRepositoryImpl(Location(context)) }
    override val moodRepository: MoodRepository by lazy { MoodRepositoryImpl(auth, firestore) }
    // fake catalog until the Room implementation of #9 is ready; then only this line changes
    override val toolRepository: ToolRepository by lazy { FakeToolRepository() }
    override val statsRepository: StatsRepository by lazy { StatsRepositoryImpl(auth, firestore) }
    // fake photos in memory until #26 saves them in Room and filesDir; then only this line changes
    override val photoRepository: PhotoRepository by lazy { FakePhotoRepository() }
    override val analyticsRepository: AnalyticsRepository by lazy { AnalyticsRepositoryImpl(Firebase.analytics) }
    // one tracker for the whole app: the tap on a tool and its screen live in different places (#8)
    override val toolLatencyTracker: ToolLatencyTracker by lazy { ToolLatencyTracker(analyticsRepository) }
    override val feedbackDao: FeedbackDao by lazy { database.feedbackDao() }
}
