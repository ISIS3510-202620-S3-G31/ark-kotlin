package com.moviles.ark.data.local.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.moviles.ark.domain.models.DecibelMeter
import com.moviles.ark.domain.models.DecibelReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.max

//lee el volumen del microfono para el scream tank
//privacidad: el audio solo pasa por un buffer en memoria que se reutiliza y se borra al final;
//no se crea ningun archivo ni se usa MediaRecorder para grabar (de MediaRecorder solo se toma la constante MIC)
class MicrophoneLevelMeter(private val context: Context) {

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    //emite una lectura cada READ_MS mientras alguien lo recolecta; al cancelar se suelta el microfono
    @SuppressLint("MissingPermission")
    fun levels(): Flow<DecibelReading> = flow {
        if (!hasPermission()) throw SecurityException("RECORD_AUDIO not granted")

        val minBufferBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBufferBytes <= 0) throw IllegalStateException("AudioRecord not supported: $minBufferBytes")

        //cada muestra pcm de 16 bits ocupa 2 bytes
        val samplesPerRead = SAMPLE_RATE * READ_MS / 1000
        val bufferBytes = max(minBufferBytes, samplesPerRead * 2 * 2)
        val buffer = ShortArray(samplesPerRead)

        val record = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufferBytes)
        try {
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord could not start")
            }
            record.startRecording()
            while (currentCoroutineContext().isActive) {
                //read espera hasta llenar el buffer (~READ_MS); se mide y se sobrescribe en la siguiente vuelta
                val read = record.read(buffer, 0, buffer.size)
                if (read < 0) throw IllegalStateException("AudioRecord read error: $read")
                if (read > 0) emit(DecibelMeter.measure(buffer, read))
            }
        } finally {
            //se suelta el microfono aunque haya error o cancelacion, y se borra lo que quedo en memoria
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                runCatching { record.stop() }
            }
            record.release()
            buffer.fill(0)
        }
    }.flowOn(Dispatchers.Default)

    companion object {
        //44100 hz es la unica frecuencia que android garantiza en todos los telefonos
        const val SAMPLE_RATE = 44100
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        //una lectura cada 50 ms: unas 20 por segundo, suficiente para que el medidor se vea fluido
        const val READ_MS = 50
    }
}
