package com.moviles.ark.data

import android.content.Context
import androidx.room.Room
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import com.moviles.ark.R
import com.moviles.ark.data.local.ArkDatabase
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.sensors.AudioPlayerHelper
import com.moviles.ark.data.local.sensors.EnvironmentalSensorManager
import com.moviles.ark.data.local.sensors.Location
import com.moviles.ark.data.local.sensors.NetworkConnectivityObserver
import com.moviles.ark.data.local.sensors.SpeechToTextHelper
import com.moviles.ark.data.local.sensors.SyncManager
import com.moviles.ark.data.repositories.AnalyticsRepositoryImpl
import com.moviles.ark.data.repositories.AuthRepositoryImpl
import com.moviles.ark.data.repositories.BreathingRepositoryImpl
import com.moviles.ark.data.repositories.FeedbackRepositoryImpl
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
import com.moviles.ark.domain.repositories.FeedbackRepository
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
    val screenTimeTracker: com.moviles.ark.domain.models.ScreenTimeTracker
    val recommendationRepository: com.moviles.ark.domain.repositories.RecommendationRepository
    val weatherServiceAdapter: com.moviles.ark.domain.adapters.WeatherServiceAdapter
    val musicServiceAdapter: com.moviles.ark.domain.adapters.MusicServiceAdapter
    val feedbackDao: FeedbackDao
    val feedbackRepository: FeedbackRepository
    val breathingRepository: BreathingRepository
    val screamTankRepository: com.moviles.ark.domain.repositories.ScreamTankRepository
    val audioPlayerHelper: AudioPlayerHelper
    val hapticFeedback: com.moviles.ark.domain.adapters.HapticFeedbackAdapter
    val networkConnectivityObserver: NetworkConnectivityObserver
    val syncRepository: SyncRepository
    val syncManager: SyncManager
    val environmentalSensorManager: EnvironmentalSensorManager
    val speechToTextHelper: SpeechToTextHelper
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    //base de datos room del telefono; private para que los viewmodels siempre pasen por un repositorio
    //lazy: obtiene la instancia unica del singleton con pool de conexiones
    private val database: ArkDatabase by lazy { ArkDatabase.getInstance(context) }

    override val auth: FirebaseAuth by lazy { Firebase.auth }
    //adapter de firebase auth para que el repositorio de login no dependa de firebase
    private val authServiceAdapter: com.moviles.ark.domain.adapters.AuthServiceAdapter by lazy { com.moviles.ark.data.remote.apis.FirebaseAuthAdapterImpl(auth) }
    override val firestore: FirebaseFirestore by lazy { Firebase.firestore }
    // copia en la nube de los jpg de la foto del dia
    private val storage: FirebaseStorage by lazy { Firebase.storage }
    override val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(authServiceAdapter, firestore, networkConnectivityObserver, onSignedIn = { syncManager.requestSync() })
    }
    // perfil guardado en room para verlo sin internet, con nombre, fecha y foto de perfil sincronizados con firestore y storage
    override val userRepository: UserRepository by lazy { UserRepositoryImpl(context, auth, firestore, storage, database.userDao()) }
    override val locationRepository: LocationRepository by lazy { LocationRepositoryImpl(Location(context)) }
    // check-ins guardados en room al instante; el sync los sube a firestore cuando hay red
    override val moodRepository: MoodRepository by lazy {
        MoodRepositoryImpl(
            auth,
            firestore,
            database.emotionCheckInDao(),
            onCheckInSaved = { syncManager.requestSync() },
            resolveCityName = locationRepository::getCityName
        )
    }
    // catalogo de herramientas en room como unica fuente de verdad, actualizado desde firestore y con copia en filesDir/tools.json
    override val toolRepository: com.moviles.ark.domain.repositories.ToolRepository by lazy {
        ToolRepositoryImpl(database.toolDao(), firestore, com.moviles.ark.data.local.ToolCatalogFileStore(context))
    }
    // interacciones con herramientas: pendientes en room hasta que el sync las sube a firestore y las borra
    // las usan la recomendacion por frecuencia del home y las estadisticas
    override val toolInteractionRepository: ToolInteractionRepository by lazy {
        ToolInteractionRepositoryImpl(database.toolRecordDao(), auth, firestore, onInteractionSaved = { syncManager.requestSync() })
    }
    override val statsRepository: StatsRepository by lazy {
        StatsRepositoryImpl(
            auth = auth,
            firestore = firestore,
            statsDiskCache = com.moviles.ark.data.local.StatsDiskCache(context.cacheDir),
            statsDailyVisitDao = database.statsDailyVisitDao(),
            onVisitRecorded = { syncManager.requestSync() },
            syncPendingData = { syncRepository.syncPendingData() }
        )
    }
    // real photo repository with Room persistence and the public gallery (MediaStore) for the jpg files
    override val photoRepository: PhotoEntryRepository by lazy {
        //cada usuario solo ve sus fotos; sus archivos van en Pictures/Ark/User_<uid>
        PhotoEntryRepositoryImpl(context, auth, database.photoEntryDao(), firestore, storage, onPhotoSaved = { syncManager.requestSync() })
    }
    override val analyticsRepository: AnalyticsRepository by lazy { AnalyticsRepositoryImpl(Firebase.analytics, auth, firestore) }
    // one tracker for the whole app: the tap on a tool and its screen live in different places
    override val toolLatencyTracker: ToolLatencyTracker by lazy { ToolLatencyTracker(analyticsRepository) }
    // one tracker for the whole app too: it counts the time between going to foreground and to background
    override val screenTimeTracker: com.moviles.ark.domain.models.ScreenTimeTracker by lazy {
        com.moviles.ark.domain.models.ScreenTimeTracker(analyticsRepository)
    }
    // ultima recomendacion del home en cacheDir/last_recommendation.json para usarla sin internet
    override val recommendationRepository: com.moviles.ark.domain.repositories.RecommendationRepository by lazy {
        com.moviles.ark.data.repositories.RecommendationRepositoryImpl(context.cacheDir)
    }
    // clima de afuera con open-meteo detras del adapter
    override val weatherServiceAdapter: com.moviles.ark.domain.adapters.WeatherServiceAdapter by lazy {
        com.moviles.ark.data.remote.apis.OpenMeteoAdapterImpl(context.cacheDir)
    }
    // adapter de musica ambiental para desacoplar el consumo de jamendo y cachear audios en disco
    override val musicServiceAdapter: com.moviles.ark.domain.adapters.MusicServiceAdapter by lazy {
        com.moviles.ark.data.remote.apis.JamendoServiceAdapterImpl(context = context)
    }
    override val feedbackDao: FeedbackDao by lazy { database.feedbackDao() }
    // repositorio de feedback desacoplado de Room y Firestore
    override val feedbackRepository: FeedbackRepository by lazy {
        FeedbackRepositoryImpl(
            feedbackDao = database.feedbackDao(),
            firestore = firestore,
            auth = auth,
            onFeedbackSaved = { syncManager.requestSync() }
        )
    }
    // repositorio de la herramienta de respiracion desacoplado mediante el adapter de musica y sesiones en room
    override val breathingRepository: BreathingRepository by lazy {
        BreathingRepositoryImpl(
            breathingSessionDao = database.breathingSessionDao(),
            toolRecordDao = database.toolRecordDao(),
            auth = auth,
            bundledAudioUri = "android.resource://${context.packageName}/${R.raw.breathing_ambient}",
            musicServiceAdapter = musicServiceAdapter,
            onInteractionSaved = { syncManager.requestSync() }
        )
    }
    // scream tank: volumen del microfono medido en memoria, sin guardar audio; la sesion va a room
    override val screamTankRepository: com.moviles.ark.domain.repositories.ScreamTankRepository by lazy {
        com.moviles.ark.data.repositories.ScreamTankRepositoryImpl(
            microphoneLevelMeter = com.moviles.ark.data.local.sensors.MicrophoneLevelMeter(context),
            toolRecordDao = database.toolRecordDao(),
            auth = auth,
            onInteractionSaved = { syncManager.requestSync() }
        )
    }
    override val audioPlayerHelper: AudioPlayerHelper by lazy { AudioPlayerHelper(context) }
    // vibracion de la respiracion guiada
    override val hapticFeedback: com.moviles.ark.domain.adapters.HapticFeedbackAdapter by lazy { com.moviles.ark.data.local.sensors.HapticFeedbackHelper(context) }
    // observador reactivo de conectividad a internet
    override val networkConnectivityObserver: NetworkConnectivityObserver by lazy { NetworkConnectivityObserver(context) }
    // repositorio y gestor de resincronizacion de datos offline
    override val syncRepository: SyncRepository by lazy {
        SyncRepositoryImpl(
            context = context,
            auth = auth,
            firestore = firestore,
            storage = storage,
            photoEntryDao = database.photoEntryDao(),
            toolRecordDao = database.toolRecordDao(),
            emotionCheckInDao = database.emotionCheckInDao(),
            feedbackDao = database.feedbackDao(),
            statsDailyVisitDao = database.statsDailyVisitDao(),
            syncPendingProfile = { userRepository.syncPendingProfile() }
        )
    }
    override val syncManager: SyncManager by lazy {
        SyncManager(context, networkConnectivityObserver, syncRepository)
    }
    // sensor de luz + hora del dia para recomendaciones segun el entorno; uno para toda la app
    override val environmentalSensorManager: EnvironmentalSensorManager by lazy { EnvironmentalSensorManager(context) }
    // ayudante reutilizable de speech-to-text nativo de android para dictado offline
    override val speechToTextHelper: SpeechToTextHelper by lazy { SpeechToTextHelper(context) }
}
