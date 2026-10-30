package com.moviles.ark.data.local.sensors

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.moviles.ark.data.remote.CrashlyticsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

//estados del ciclo de vida del reconocimiento de voz segun el requerimiento
enum class SpeechRecognitionState {
    IDLE,
    LISTENING,
    PROCESSING,
    ERROR
}

//ayudante reutilizable para dictado por voz envolviendo el speechrecognizer nativo de android
//diseñado para funcionar sin conexion (offline-first) y gestionar el ciclo de vida del microfono
class SpeechToTextHelper(
    private val context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(SpeechRecognitionState.IDLE)
    val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isOfflineSupported = MutableStateFlow(false)
    val isOfflineSupported: StateFlow<Boolean> = _isOfflineSupported.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    //true desde que el usuario toca el microfono hasta que toca parar: el reconocedor se reinicia solo en cada pausa
    private var keepListening = false
    //frases ya reconocidas en esta sesion de dictado; el texto parcial se suma al final
    private var sessionText = ""
    //ultimo texto parcial que se ve en la caja y todavia no llega como resultado final
    private var lastPartial = ""
    //caja de texto que abrio el dictado: el helper es uno para toda la app y solo ella recibe lo dictado
    private var sessionOwner: Any? = null
    private var preloadRequested = false
    private var switchedRecognizer = false
    private var userAskedForPack = false
    //true sin internet: reconocedor del telefono con su paquete offline; false con internet: reconocedor normal en linea
    private var useOnDevice = true
    //idioma con paquete offline que mas se parece al del telefono (por ejemplo es-US si es-CO no tiene)
    private var recognitionLanguageTag: String = Locale.getDefault().toLanguageTag()

    //verifica si el usuario concedio el permiso de audio
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    //verifica si el servicio de reconocimiento de voz esta instalado en el telefono
    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    //revisa si el hardware/sistema soporta reconocimiento estrictamente en el dispositivo
    fun isOnDeviceRecognitionAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            isRecognitionAvailable()
        }
    }

    //se llama cuando la app queda en pantalla (la primera vez es justo despues de instalarla): elige el idioma y pide bajar
    //su paquete offline; android no deja correr codigo al instalar, asi que este es el primer momento posible
    fun preloadOfflineModel() {
        //una vez por apertura de la app, aunque la pantalla vuelva a primer plano varias veces
        if (preloadRequested) return
        preloadRequested = true
        scope.launch(Dispatchers.IO) {
            try {
                _isOfflineSupported.value = isOnDeviceRecognitionAvailable()
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !_isOfflineSupported.value) return@launch
                mainHandler.post { checkAndDownloadLanguagePack() }
            } catch (e: Exception) {
                Log.w("SpeechToTextHelper", "Error preloading offline speech model", e)
            }
        }
    }

    //el reconocedor se conecta al servicio de voz despues de crearlo; si se le pregunta antes responde
    //"not connected to the recognition service" y nunca contesta, por eso se le da un listener y se espera
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkAndDownloadLanguagePack(attempt: Int = 1) {
        try {
            val checker = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            checker.setRecognitionListener(createRecognitionListener(forSupportCheck = true))
            var answered = false
            mainHandler.postDelayed({ askLanguageSupport(checker) { answered = true } }, CONNECT_DELAY_MILLIS)
            //sin respuesta a tiempo se cierra y se intenta una vez mas
            mainHandler.postDelayed({
                if (!answered) {
                    checker.destroy()
                    if (attempt < SUPPORT_CHECK_ATTEMPTS) checkAndDownloadLanguagePack(attempt + 1)
                }
            }, SUPPORT_CHECK_TIMEOUT_MILLIS)
        } catch (e: Exception) {
            Log.w("SpeechToTextHelper", "Could not check on-device recognition support", e)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun askLanguageSupport(checker: SpeechRecognizer, onAnswer: () -> Unit) {
        try {
            checker.checkRecognitionSupport(
                createRecognizerIntent(recognitionLanguageTag),
                ContextCompat.getMainExecutor(context),
                object : android.speech.RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: android.speech.RecognitionSupport) {
                        onAnswer()
                        val installed = recognitionSupport.installedOnDeviceLanguages
                        val supported = recognitionSupport.supportedOnDeviceLanguages + installed
                        val tag = bestLanguageTag(Locale.getDefault(), installed, supported)
                        Log.d("SpeechToTextHelper", "On-device speech: device=${Locale.getDefault().toLanguageTag()} chosen=$tag installed=$installed")
                        _isOfflineSupported.value = tag != null
                        if (tag != null) {
                            recognitionLanguageTag = tag
                            //solo se pide si falta; si ya esta instalado no se baja de nuevo
                            //android muestra su propia ventana de descarga: al abrir la app se pide una sola vez por instalacion
                            //y despues solo si el usuario toca el microfono y falta el paquete
                            //si ya se esta bajando (android 14+ lo dice) no se vuelve a mostrar la ventana
                            val pending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) recognitionSupport.pendingOnDeviceLanguages else emptyList()
                            val missing = (installed + pending).none { it.equals(tag, ignoreCase = true) }
                            if (missing && (userAskedForPack || !packAlreadyRequested())) requestLanguagePack(tag)
                            userAskedForPack = false
                        }
                        checker.destroy()
                    }

                    override fun onError(errorCode: Int) {
                        onAnswer()
                        Log.w("SpeechToTextHelper", "On-device speech support check failed: $errorCode")
                        checker.destroy()
                    }
                }
            )
        } catch (e: Exception) {
            Log.w("SpeechToTextHelper", "Could not check on-device recognition support", e)
        }
    }

    //el pedido de descarga necesita un reconocedor conectado: el de la consulta queda desconectado al responder
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestLanguagePack(tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PACK_REQUESTED_KEY, true).apply()
        val downloader = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        downloader.setRecognitionListener(createRecognitionListener(forSupportCheck = true))
        mainHandler.postDelayed({
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    //android 14+ avisa si el paquete quedo programado (lo baja cargando y en reposo) o si ya termino
                    downloader.triggerModelDownload(
                        createRecognizerIntent(tag),
                        ContextCompat.getMainExecutor(context),
                        object : android.speech.ModelDownloadListener {
                            override fun onProgress(completedPercent: Int) {}
                            override fun onSuccess() {
                                Log.i("SpeechToTextHelper", "Offline speech pack installed for $tag")
                                downloader.destroy()
                            }
                            override fun onScheduled() {
                                Log.i("SpeechToTextHelper", "Offline speech pack scheduled for $tag")
                                downloader.destroy()
                            }
                            override fun onError(error: Int) {
                                Log.w("SpeechToTextHelper", "Offline speech pack download failed for $tag: $error")
                                downloader.destroy()
                            }
                        }
                    )
                } else {
                    downloader.triggerModelDownload(createRecognizerIntent(tag))
                    mainHandler.postDelayed({ downloader.destroy() }, SUPPORT_CHECK_TIMEOUT_MILLIS)
                }
                Log.i("SpeechToTextHelper", "Offline speech pack requested for $tag")
            } catch (e: Exception) {
                Log.w("SpeechToTextHelper", "Could not request offline speech pack", e)
                downloader.destroy()
            }
        }, CONNECT_DELAY_MILLIS)
    }

    private fun packAlreadyRequested(): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(PACK_REQUESTED_KEY, false)

    //el usuario quiso dictar sin red y falta el paquete: se vuelve a preguntar y a ofrecer la descarga
    private fun offerLanguagePackAgain() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        userAskedForPack = true
        checkAndDownloadLanguagePack()
    }

    //el idioma exacto del telefono si tiene paquete offline; si no, uno del mismo idioma (primero los ya instalados)
    //entre varios del mismo idioma se prefiere el de estados unidos: es el español latino (es-US) y el ingles americano
    private fun bestLanguageTag(device: Locale, installed: List<String>, supported: List<String>): String? {
        val exact = device.toLanguageTag()
        supported.firstOrNull { it.equals(exact, ignoreCase = true) }?.let { return it }
        val sameLanguage = supported.filter { Locale.forLanguageTag(it).language == device.language }
        return installed.firstOrNull { Locale.forLanguageTag(it).language == device.language }
            ?: sameLanguage.firstOrNull { Locale.forLanguageTag(it).country == "US" }
            ?: sameLanguage.firstOrNull()
    }

    fun isOwnedBy(owner: Any): Boolean = sessionOwner === owner

    //inicia el dictado: sigue escuchando aunque el usuario haga pausas, hasta que toque parar
    fun startListening(owner: Any) {
        sessionOwner = owner
        mainHandler.post {
            if (!hasPermission()) {
                _state.value = SpeechRecognitionState.ERROR
                _errorMessage.value = MIC_PERMISSION_NOTICE
                return@post
            }

            if (!isRecognitionAvailable()) {
                _state.value = SpeechRecognitionState.ERROR
                _errorMessage.value = VOICE_UNAVAILABLE_NOTICE
                return@post
            }

            _errorMessage.value = null
            _transcription.value = ""
            sessionText = ""
            lastPartial = ""
            keepListening = true
            //con internet se usa el reconocedor normal (en linea, no necesita paquete); sin internet el del telefono
            useOnDevice = !isOnline()
            switchedRecognizer = false
            startRecognizer()
        }
    }

    //crea el reconocedor y empieza a escuchar; se vuelve a llamar en cada pausa mientras keepListening sea true
    private fun startRecognizer() {
        //limpia cualquier instancia anterior antes de arrancar
        destroyInternal()
        _soundLevel.value = 0f
        _state.value = SpeechRecognitionState.LISTENING

        try {
            //en android 12+ usa el reconocedor en el dispositivo para garantizar funcionamiento offline
            val recognizer = if (useOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            recognizer.setRecognitionListener(createRecognitionListener())
            speechRecognizer = recognizer
            recognizer.startListening(createRecognizerIntent(recognitionLanguageTag, preferOffline = useOnDevice))
        } catch (e: Exception) {
            Log.e("SpeechToTextHelper", "Error starting speech recognition", e)
            CrashlyticsHelper.logNonFatal(
                componentName = "SpeechToTextHelper",
                action = "startListening",
                throwable = e
            )
            keepListening = false
            _state.value = SpeechRecognitionState.ERROR
            _errorMessage.value = VOICE_UNAVAILABLE_NOTICE
            destroyInternal()
        }
    }

    //el usuario toco parar: se procesa lo ultimo que dijo y se termina la sesion
    fun stopListening() {
        mainHandler.post {
            keepListening = false
            if (_state.value == SpeechRecognitionState.LISTENING) {
                _state.value = SpeechRecognitionState.PROCESSING
                try {
                    speechRecognizer?.stopListening()
                } catch (e: Exception) {
                    Log.w("SpeechToTextHelper", "Error stopping speech recognizer", e)
                    _state.value = SpeechRecognitionState.IDLE
                }
            }
        }
    }

    //la caja de texto se va (se sale del feedback o del check-in): se apaga el microfono aunque siga escuchando
    //y se olvida lo dictado, asi el siguiente feedback empieza con su caja vacia
    fun release(owner: Any) {
        if (sessionOwner !== owner) return
        sessionOwner = null
        cancel()
    }

    //cancela la escucha inmediatamente sin procesar y borra lo dictado
    fun cancel() {
        mainHandler.post {
            keepListening = false
            sessionText = ""
            lastPartial = ""
            _transcription.value = ""
            _errorMessage.value = null
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w("SpeechToTextHelper", "Error cancelling speech recognizer", e)
            } finally {
                destroyInternal()
                _state.value = SpeechRecognitionState.IDLE
                _soundLevel.value = 0f
            }
        }
    }

    //libera completamente los recursos del microfono y el reconocedor
    fun destroy() {
        mainHandler.post {
            keepListening = false
            destroyInternal()
            _state.value = SpeechRecognitionState.IDLE
            _soundLevel.value = 0f
        }
    }

    fun resetTranscription() {
        _transcription.value = ""
    }

    private fun destroyInternal() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("SpeechToTextHelper", "Error destroying speech recognizer", e)
        } finally {
            speechRecognizer = null
        }
    }

    //preferOffline obliga al reconocedor a usar solo el paquete del telefono: con internet y sin paquete fallaba con error 13
    private fun createRecognizerIntent(languageTag: String, preferOffline: Boolean = true): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            //forzar la preferencia de paquetes locales offline sin internet
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            //pausas largas antes de cortar; si igual corta, el helper vuelve a escuchar solo
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
    }

    //forSupportCheck: el reconocedor que solo pregunta por los idiomas no debe tocar el estado del dictado
    private fun createRecognitionListener(forSupportCheck: Boolean = false): RecognitionListener {
        if (forSupportCheck) return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onResults(results: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = SpeechRecognitionState.LISTENING
            }

            override fun onBeginningOfSpeech() {
                _state.value = SpeechRecognitionState.LISTENING
            }

            override fun onRmsChanged(rmsdB: Float) {
                //normaliza los decibeles (habitualmente -2dB a 10dB) en una escala de 0 a 1 para animaciones
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _soundLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _soundLevel.value = 0f
                //mientras el usuario no toque parar se sigue mostrando que escucha
                if (!keepListening) _state.value = SpeechRecognitionState.PROCESSING
            }

            override fun onError(error: Int) {
                _soundLevel.value = 0f
                Log.w("SpeechToTextHelper", "Speech recognition error code: $error")
                destroyInternal()
                val isSilence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                val packMissing = error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE
                when {
                    //cada sesion puede cambiar de reconocedor una sola vez, sin aviso, para no rebotar entre los dos
                    //sin paquete offline pero con internet: sigue con el reconocedor en linea
                    packMissing && useOnDevice && keepListening && !switchedRecognizer && isOnline() -> {
                        useOnDevice = false
                        switchedRecognizer = true
                        restartAfterPause()
                    }
                    //el reconocedor en linea perdio la red: sigue con el del telefono por si tiene el paquete
                    //lo que ya se veia escrito se guarda antes de cambiar; si no, el primer parcial del nuevo lo borraba
                    isNetworkError(error) && !useOnDevice && keepListening && !switchedRecognizer -> {
                        sessionText = joinText(sessionText, lastPartial)
                        lastPartial = ""
                        _transcription.value = sessionText
                        useOnDevice = true
                        switchedRecognizer = true
                        restartAfterPause()
                    }
                    //una pausa sin palabras: si el usuario no ha tocado parar, se vuelve a escuchar
                    isSilence && keepListening -> restartAfterPause()
                    //silencio despues de tocar parar: termina sin aviso
                    isSilence -> _state.value = SpeechRecognitionState.IDLE
                    else -> {
                        keepListening = false
                        _state.value = SpeechRecognitionState.ERROR
                        _errorMessage.value = noticeFor(error)
                        if (packMissing) offerLanguagePackAgain()
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                _soundLevel.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                lastPartial = ""
                sessionText = joinText(sessionText, text)
                _transcription.value = sessionText
                destroyInternal()
                if (keepListening) restartAfterPause() else _state.value = SpeechRecognitionState.IDLE
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: return
                if (text.isNotBlank()) {
                    lastPartial = text
                    _transcription.value = joinText(sessionText, text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    //el reconocedor necesita un respiro antes de volver a abrir el microfono
    private fun restartAfterPause() {
        mainHandler.postDelayed({ if (keepListening) startRecognizer() }, RESTART_DELAY_MILLIS)
    }

    private fun isNetworkError(error: Int): Boolean = error == SpeechRecognizer.ERROR_NETWORK ||
        error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT || error == SpeechRecognizer.ERROR_SERVER

    private fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun joinText(previous: String, next: String): String = listOf(previous, next).filter { it.isNotBlank() }.joinToString(" ")

    //avisos cortos: la caja de texto los muestra en una sola linea junto al microfono
    private fun noticeFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> MIC_PERMISSION_NOTICE
        //12 y 13: el paquete de idioma offline no esta (o aun se esta bajando)
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> OFFLINE_NOT_READY_NOTICE
        else -> VOICE_UNAVAILABLE_NOTICE
    }

    companion object {
        const val MIC_PERMISSION_NOTICE = "No mic access. Please type."
        const val VOICE_UNAVAILABLE_NOTICE = "Voice not available. Please type."
        const val OFFLINE_NOT_READY_NOTICE = "Offline voice not ready. Please type."
        private const val RESTART_DELAY_MILLIS = 250L
        private const val CONNECT_DELAY_MILLIS = 1_500L
        private const val PREFS_NAME = "speech_to_text"
        private const val PACK_REQUESTED_KEY = "offline_pack_requested"
        private const val SUPPORT_CHECK_TIMEOUT_MILLIS = 6_000L
        private const val SUPPORT_CHECK_ATTEMPTS = 2
        //SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED y ERROR_LANGUAGE_UNAVAILABLE (api 31)
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
