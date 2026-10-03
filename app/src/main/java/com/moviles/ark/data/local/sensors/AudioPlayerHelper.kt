package com.moviles.ark.data.local.sensors

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

//helper para reproducir audio ambiental de jamendo con el mediaplayer de android (#7)
class AudioPlayerHelper(
    private val context: Context? = null
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentUrl: String? = null
    private var isPrepared = false

    //inicia la reproduccion de una url de audio
    //onError avisa si no se pudo reproducir (por ejemplo una pista de internet sin conexion) (#82)
    fun playUrl(
        url: String,
        onPlaybackStateChanged: (isPlaying: Boolean) -> Unit = {},
        onError: () -> Unit = {}
    ) {
        if (url == currentUrl && mediaPlayer != null && isPrepared) {
            mediaPlayer?.start()
            onPlaybackStateChanged(true)
            return
        }

        stop()
        currentUrl = url
        isPrepared = false

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                //las pistas que vienen dentro de la app (res/raw) se abren con el context (#82)
                if (url.startsWith(LOCAL_RESOURCE_SCHEME) && context != null) {
                    setDataSource(context, Uri.parse(url))
                } else {
                    setDataSource(url)
                }
                isLooping = true //reproduccion continua para la sesion de respiracion
                setOnPreparedListener { mp ->
                    isPrepared = true
                    mp.start()
                    onPlaybackStateChanged(true)
                }
                setOnErrorListener { _, _, _ ->
                    isPrepared = false
                    onPlaybackStateChanged(false)
                    onError()
                    true
                }
                setOnCompletionListener {
                    onPlaybackStateChanged(false)
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
            onPlaybackStateChanged(false)
            onError()
        }
    }

    //pausa la reproduccion actual
    fun pause() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    //reanuda la reproduccion si estaba pausada
    fun resume(): Boolean {
        return if (mediaPlayer != null && isPrepared && mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
            true
        } else {
            false
        }
    }

    //detiene por completo la reproduccion
    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            isPrepared = false
        }
    }

    //indica si el reproductor esta sonando
    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }

    //libera los recursos del reproductor
    fun release() {
        stop()
    }

    companion object {
        private const val LOCAL_RESOURCE_SCHEME = "android.resource://"
    }
}
