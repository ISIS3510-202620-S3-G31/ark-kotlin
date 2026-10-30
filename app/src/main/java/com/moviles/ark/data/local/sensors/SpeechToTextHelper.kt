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

    //se llama al abrir la app (la primera vez es justo despues de instalarla): elige el idioma y pide bajar su paquete offline
    //android no deja correr codigo al instalar, asi que este es el primer momento posible; el sistema lo baja en segundo plano
    fun preloadOfflineModel() {
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

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkAndDownloadLanguagePack() {
        try {
            val checker = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            checker.checkRecognitionSupport(
                createRecognizerIntent(recognitionLanguageTag),
                ContextCompat.getMainExecutor(context),
                object : android.speech.RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: android.speech.RecognitionSupport) {
                        val installed = recognitionSupport.installedOnDeviceLanguages
                        val supported = recognitionSupport.supportedOnDeviceLanguages + installed
                        val tag = bestLanguageTag(Locale.getDefault(), installed, supported)
                        _isOfflineSupported.value = tag != null
                        if (tag != null) {
                            recognitionLanguageTag = tag
                            //solo se pide si falta; si ya esta instalado no se baja de nuevo
                            if (installed.none { it.equals(tag, ignoreCase = true) }) {
                                checker.triggerModelDownload(createRecognizerIntent(tag))
                                Log.d("SpeechToTextHelper", "Offline speech pack requested for $tag")
                            }
                        }
                        checker.destroy()
                    }

                    override fun onError(errorCode: Int) {
                        checker.destroy()
                    }
                }
            )
        } catch (e: Exception) {
            Log.w("SpeechToTextHelper", "Could not check on-device recognition support", e)
        }
    }

    //el idioma exacto del telefono si tiene paquete offline; si no, uno del mismo idioma (primero los ya instalados)
    private fun bestLanguageTag(device: Locale, installed: List<String>, supported: List<String>): String? {
        val exact = device.toLanguageTag()
        supported.firstOrNull { it.equals(exact, ignoreCase = true) }?.let { return it }
        val sameLanguage = { tag: String -> Locale.forLanguageTag(tag).language == device.language }
        return installed.firstOrNull(sameLanguage) ?: supported.firstOrNull(sameLanguage)
    }

    //inicia el dictado: sigue escuchando aunque el usuario haga pausas, hasta que toque parar
    fun startListening() {
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
            keepListening = true
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
            val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            recognizer.setRecognitionListener(createRecognitionListener())
            speechRecognizer = recognizer
            recognizer.startListening(createRecognizerIntent(recognitionLanguageTag))
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

    //cancela la escucha inmediatamente sin procesar
    fun cancel() {
        mainHandler.post {
            keepListening = false
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

    private fun createRecognizerIntent(languageTag: String): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            //forzar la preferencia de paquetes locales offline sin internet
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            //pausas largas antes de cortar; si igual corta, el helper vuelve a escuchar solo
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
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
                when {
                    //una pausa sin palabras: si el usuario no ha tocado parar, se vuelve a escuchar
                    isSilence && keepListening -> restartAfterPause()
                    //silencio despues de tocar parar: termina sin aviso
                    isSilence -> _state.value = SpeechRecognitionState.IDLE
                    else -> {
                        keepListening = false
                        _state.value = SpeechRecognitionState.ERROR
                        _errorMessage.value = noticeFor(error)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                _soundLevel.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                sessionText = joinText(sessionText, text)
                _transcription.value = sessionText
                destroyInternal()
                if (keepListening) restartAfterPause() else _state.value = SpeechRecognitionState.IDLE
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: return
                if (text.isNotBlank()) {
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

    private fun joinText(previous: String, next: String): String = listOf(previous, next).filter { it.isNotBlank() }.joinToString(" ")

    //avisos cortos: la caja de texto los muestra en una sola linea junto al microfono
    private fun noticeFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> MIC_PERMISSION_NOTICE
        //12 y 13: el paquete de idioma offline no esta (o aun se esta bajando)
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, 12, 13 -> OFFLINE_NOT_READY_NOTICE
        else -> VOICE_UNAVAILABLE_NOTICE
    }

    companion object {
        const val MIC_PERMISSION_NOTICE = "No mic access. Please type."
        const val VOICE_UNAVAILABLE_NOTICE = "Voice not available. Please type."
        const val OFFLINE_NOT_READY_NOTICE = "Offline voice not ready. Please type."
        private const val RESTART_DELAY_MILLIS = 250L
    }
}
