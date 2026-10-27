package com.moviles.ark.data

import android.content.Context
import androidx.room.Room
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.moviles.ark.R
import com.moviles.ark.data.local.ArkDatabase
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.sensors.AudioPlayerHelper
import com.moviles.ark.data.local.sensors.EnvironmentalSensorManager
import com.moviles.ark.data.local.sensors.Location
import com.moviles.ark.data.local.sensors.NetworkConnectivityObserver
import com.moviles.ark.data.local.sensors.SyncManager
import com.moviles.ark.data.repositories.AnalyticsRepositoryImpl
import com.moviles.ark.data.repositories.AuthRepositoryImpl
import com.moviles.ark.data.repositories.BreathingRepositoryImpl
import com.moviles.ark.data.repositories.ToolRepositoryImpl
import com.moviles.ark.data.repositories.LocationRepositoryImpl
import com.moviles.ark.data.repositories.MoodRepositoryImpl
import com.moviles.ark.data.repositories.PhotoEntryRepositoryImpl
import com.moviles.ark.data.repositories.StatsRepositoryImpl
import com.moviles.ark.data.repositories.SyncRepositoryImpl
import com.moviles.ark.data.repositories.ToolInteractionRepositoryImpl
import com.moviles.ark.data.repositories.UserRepositoryImpl
import com.moviles.ark.domain.models.ToolLatencyTracker
import com.moviles.ark.domain.repositories.AnalyticsRepository
import com.moviles.ark.domain.repositories.AuthRepository
import com.moviles.ark.domain.repositories.BreathingRepository
import com.moviles.ark.domain.repositories.LocationRepository
import com.moviles.ark.domain.repositories.MoodRepository
import com.moviles.ark.domain.repositories.PhotoEntryRepository
import com.moviles.ark.domain.repositories.StatsRepository
import com.moviles.ark.domain.repositories.SyncRepository
import com.moviles.ark.domain.repositories.ToolInteractionRepository
import com.moviles.ark.domain.repositories.UserRepository

interface AppContainer {
    val auth: FirebaseAuth
    val firestore: FirebaseFirestore
    val authRepository: AuthRepository
    val userRepository: UserRepository
    val locationRepository: LocationRepository
    val moodRepository: MoodRepository
    val toolRepository: com.moviles.ark.domain.repositories.ToolRepository
    val toolInteractionRepository: ToolInteractionRepository
    val statsRepository: StatsRepository
    val photoRepository: PhotoEntryRepository
    val analyticsRepository: AnalyticsRepository
    val toolLatencyTracker: ToolLatencyTracker
    val recommendationRepository: com.moviles.ark.domain.repositories.RecommendationRepository
    val weatherServiceAdapter: com.moviles.ark.domain.adapters.WeatherServiceAdapter
    val musicServiceAdapter: com.moviles.ark.domain.adapters.MusicServiceAdapter
    val feedbackDao: FeedbackDao
    val breathingRepository: BreathingRepository
    val screamTankRepository: com.moviles.ark.domain.repositories.ScreamTankRepository
    val audioPlayerHelper: AudioPlayerHelper
    val networkConnectivityObserver: NetworkConnectivityObserver
    val syncRepository: SyncRepository
    val syncManager: SyncManager
    val environmentalSensorManager: EnvironmentalSensorManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    //base de datos room del telefono; private para que los viewmodels siempre pasen por un repositorio
    //lazy: no se crea hasta que un repositorio la use
    private val database: ArkDatabase by lazy {
        Room.databaseBuilder(context, ArkDatabase::class.java, "ark.db")
            //mientras desarrollamos: si cambia una tabla se borra la base y se crea otra (se pierden los datos locales)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    override val auth: FirebaseAuth by lazy { Firebase.auth }
    //adapter de firebase auth para que el repositorio de login no dependa de firebase
    private val authServiceAdapter: com.moviles.ark.domain.adapters.AuthServiceAdapter by lazy { com.moviles.ark.data.remote.apis.FirebaseAuthAdapterImpl(auth) }
    override val firestore: FirebaseFirestore by lazy { Firebase.firestore }
    override val authRepository: AuthRepository by lazy { AuthRepositoryImpl(authServiceAdapter, firestore, networkConnectivityObserver) }
    // perfil guardado en room para verlo sin internet, con nombre y fecha sincronizados con firestore
    override val userRepository: UserRepository by lazy { UserRepositoryImpl(context, auth, firestore, database.userDao()) }
    override val locationRepository: LocationRepository by lazy { LocationRepositoryImpl(Location(context)) }
    // check-ins guardados en room al instante; el sync los sube a firestore cuando hay red
    override val moodRepository: MoodRepository by lazy {
        MoodRepositoryImpl(auth, firestore, database.emotionCheckInDao(), onCheckInSaved = { syncManager.requestSync() })
    }
    // catalogo de herramientas en room como unica fuente de verdad, actualizado desde firestore y con copia en filesDir/tools.json
    override val toolRepository: com.moviles.ark.domain.repositories.ToolRepository by lazy {
        ToolRepositoryImpl(database.toolDao(), firestore, com.moviles.ark.data.local.ToolCatalogFileStore(context))
    }
    // interacciones con herramientas guardadas en room; las usa la recomendacion por frecuencia del home
    override val toolInteractionRepository: ToolInteractionRepository by lazy { ToolInteractionRepositoryImpl(database.toolRecordDao()) }
    override val statsRepository: StatsRepository by lazy { StatsRepositoryImpl(auth, firestore, database.emotionCheckInDao(), database.toolRecordDao(), com.moviles.ark.data.local.StatsDiskCache(context.cacheDir)) }
    // real photo repository with Room persistence and the public gallery (MediaStore) for the jpg files
    override val photoRepository: PhotoEntryRepository by lazy {
        //cada usuario solo ve sus fotos; sus archivos van en Pictures/Ark/User_<uid>
        PhotoEntryRepositoryImpl(context, auth, database.photoEntryDao())
    }
    override val analyticsRepository: AnalyticsRepository by lazy { AnalyticsRepositoryImpl(Firebase.analytics) }
    // one tracker for the whole app: the tap on a tool and its screen live in different places
    override val toolLatencyTracker: ToolLatencyTracker by lazy { ToolLatencyTracker(analyticsRepository) }
    // ultima recomendacion del home en cacheDir/last_recommendation.json para usarla sin internet
    override val recommendationRepository: com.moviles.ark.domain.repositories.RecommendationRepository by lazy {
        com.moviles.ark.data.repositories.RecommendationRepositoryImpl(context.cacheDir)
    }
    // clima de afuera con open-meteo detras del adapter
    override val weatherServiceAdapter: com.moviles.ark.domain.adapters.WeatherServiceAdapter by lazy {
        com.moviles.ark.data.remote.apis.OpenMeteoAdapterImpl()
    }
    // adapter de musica ambiental para desacoplar el consumo de jamendo y cachear audios en disco
    override val musicServiceAdapter: com.moviles.ark.domain.adapters.MusicServiceAdapter by lazy {
        com.moviles.ark.data.remote.apis.JamendoServiceAdapterImpl(
            context = context,
            bundledAudioUri = "android.resource://${context.packageName}/${R.raw.breathing_ambient}"
        )
    }
    override val feedbackDao: FeedbackDao by lazy { database.feedbackDao() }
    // repositorio de la herramienta de respiracion desacoplado mediante el adapter de musica y sesiones en room
    override val breathingRepository: BreathingRepository by lazy {
        BreathingRepositoryImpl(
            breathingSessionDao = database.breathingSessionDao(),
            toolRecordDao = database.toolRecordDao(),
            auth = auth,
            bundledAudioUri = "android.resource://${context.packageName}/${R.raw.breathing_ambient}",
            musicServiceAdapter = musicServiceAdapter
        )
    }
    // scream tank: volumen del microfono medido en memoria, sin guardar audio; la sesion va a room
    override val screamTankRepository: com.moviles.ark.domain.repositories.ScreamTankRepository by lazy {
        com.moviles.ark.data.repositories.ScreamTankRepositoryImpl(
            microphoneLevelMeter = com.moviles.ark.data.local.sensors.MicrophoneLevelMeter(context),
            toolRecordDao = database.toolRecordDao(),
            auth = auth
        )
    }
    override val audioPlayerHelper: AudioPlayerHelper by lazy { AudioPlayerHelper(context) }
    // observador reactivo de conectividad a internet
    override val networkConnectivityObserver: NetworkConnectivityObserver by lazy { NetworkConnectivityObserver(context) }
    // repositorio y gestor de resincronizacion de datos offline
    override val syncRepository: SyncRepository by lazy {
        SyncRepositoryImpl(
            auth = auth,
            firestore = firestore,
            photoEntryDao = database.photoEntryDao(),
            toolRecordDao = database.toolRecordDao(),
            emotionCheckInDao = database.emotionCheckInDao(),
            feedbackDao = database.feedbackDao()
        )
    }
    override val syncManager: SyncManager by lazy {
        SyncManager(networkConnectivityObserver, syncRepository)
    }
    // sensor de luz + hora del dia para recomendaciones segun el entorno; uno para toda la app
    override val environmentalSensorManager: EnvironmentalSensorManager by lazy { EnvironmentalSensorManager(context) }
}

