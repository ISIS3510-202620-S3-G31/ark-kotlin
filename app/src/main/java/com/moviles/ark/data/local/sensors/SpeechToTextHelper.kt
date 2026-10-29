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

    //inicia o calienta el motor de reconocimiento offline desde el arranque de la app
    fun preloadOfflineModel() {
        scope.launch(Dispatchers.IO) {
            try {
                val available = isRecognitionAvailable()
                val onDeviceAvailable = isOnDeviceRecognitionAvailable()
                _isOfflineSupported.value = onDeviceAvailable

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val recognizerIntent = createRecognizerIntent(Locale.getDefault())
                    mainHandler.post {
                        try {
                            val tempRecognizer = if (onDeviceAvailable) {
                                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                            } else {
                                SpeechRecognizer.createSpeechRecognizer(context)
                            }
                            tempRecognizer.checkRecognitionSupport(
                                recognizerIntent,
                                ContextCompat.getMainExecutor(context),
                                object : android.speech.RecognitionSupportCallback {
                                    override fun onSupportResult(recognitionSupport: android.speech.RecognitionSupport) {
                                        val supportedLocales = recognitionSupport.supportedOnDeviceLanguages
                                        val isLocaleSupported = supportedLocales.any {
                                            it.equals(Locale.getDefault().toLanguageTag(), ignoreCase = true) ||
                                            it.startsWith(Locale.getDefault().language, ignoreCase = true)
                                        }
                                        _isOfflineSupported.value = isLocaleSupported
                                        //si el paquete de idioma local no esta instalado, solicita la descarga en segundo plano
                                        if (isLocaleSupported && !recognitionSupport.installedOnDeviceLanguages.contains(Locale.getDefault().toLanguageTag())) {
                                            tempRecognizer.triggerModelDownload(recognizerIntent)
                                        }
                                        tempRecognizer.destroy()
                                    }

                                    override fun onError(errorCode: Int) {
                                        tempRecognizer.destroy()
                                    }
                                }
                            )
                        } catch (e: Exception) {
                            Log.w("SpeechToTextHelper", "Could not check on-device recognition support", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("SpeechToTextHelper", "Error preloading offline speech model", e)
            }
        }
    }

    //inicia la escucha activa de dictado; se asegura de correr en el hilo principal
    fun startListening(language: Locale = Locale.getDefault()) {
        mainHandler.post {
            if (!hasPermission()) {
                _state.value = SpeechRecognitionState.ERROR
                _errorMessage.value = "Microphone permission is required for voice dictation"
                return@post
            }

            if (!isRecognitionAvailable()) {
                _state.value = SpeechRecognitionState.ERROR
                _errorMessage.value = "Speech recognition is not available on this device"
                return@post
            }

            //limpia cualquier instancia anterior antes de arrancar
            destroyInternal()

            _errorMessage.value = null
            _transcription.value = ""
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

                val intent = createRecognizerIntent(language)
                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e("SpeechToTextHelper", "Error starting speech recognition", e)
                CrashlyticsHelper.logNonFatal(
                    componentName = "SpeechToTextHelper",
                    action = "startListening",
                    throwable = e
                )
                _state.value = SpeechRecognitionState.ERROR
                _errorMessage.value = e.localizedMessage ?: "Could not start voice recognition"
                destroyInternal()
            }
        }
    }

    //detiene la grabacion pero procesa lo que se haya escuchado
    fun stopListening() {
        mainHandler.post {
            if (_state.value == SpeechRecognitionState.LISTENING) {
                _state.value = SpeechRecognitionState.PROCESSING
                try {
                    speechRecognizer?.stopListening()
                } catch (e: Exception) {
                    Log.w("SpeechToTextHelper", "Error stopping speech recognizer", e)
                }
            }
        }
    }

    //cancela la escucha inmediatamente sin procesar
    fun cancel() {
        mainHandler.post {
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

    private fun createRecognizerIntent(language: Locale): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            //forzar la preferencia de paquetes locales offline sin internet
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
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
                _state.value = SpeechRecognitionState.PROCESSING
                _soundLevel.value = 0f
            }

            override fun onError(error: Int) {
                _soundLevel.value = 0f
                val message = mapErrorCodeToMessage(error)
                Log.w("SpeechToTextHelper", "Speech recognition error code: $error - $message")
                //errores de silencio o no coincidencia vuelven a IDLE para no dejar bloqueada la UI
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    _state.value = SpeechRecognitionState.IDLE
                    _errorMessage.value = message
                } else {
                    _state.value = SpeechRecognitionState.ERROR
                    _errorMessage.value = message
                }
                destroyInternal()
            }

            override fun onResults(results: Bundle?) {
                _soundLevel.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                if (text.isNotBlank()) {
                    _transcription.value = text
                }
                _state.value = SpeechRecognitionState.IDLE
                destroyInternal()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: return
                if (text.isNotBlank()) {
                    _transcription.value = text
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun mapErrorCodeToMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check your microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Client recognition error. Please try again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Tap to try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service is currently busy."
            SpeechRecognizer.ERROR_SERVER -> "Recognition server error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
            else -> "Speech recognition error ($error)"
        }
    }
}
