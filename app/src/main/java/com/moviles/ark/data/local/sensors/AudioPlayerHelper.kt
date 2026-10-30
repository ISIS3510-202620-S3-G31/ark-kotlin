package com.moviles.ark.data.local.sensors

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.moviles.ark.domain.adapters.AmbientAudioPlayer

//helper para reproducir audio ambiental de jamendo con el mediaplayer de android
class AudioPlayerHelper(
    private val context: Context? = null
) : AmbientAudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var currentUrl: String? = null
    private var isPrepared = false
    //false si el usuario pauso mientras la pista cargaba: al terminar de cargar no debe empezar sola
    private var playWhenReady = false
    //aviso de la ultima pista pedida; resume tambien lo usa para decir que volvio a sonar
    private var onStateChanged: (isPlaying: Boolean) -> Unit = {}

    //inicia la reproduccion de una url de audio; las remotas suenan por streaming mientras cargan
    //onError avisa si no se pudo reproducir (por ejemplo una pista de internet sin conexion)
    override fun playUrl(
        url: String,
        onPlaybackStateChanged: (isPlaying: Boolean) -> Unit,
        onError: () -> Unit
    ) {
        playWhenReady = true
        onStateChanged = onPlaybackStateChanged
        val loadedPlayer = mediaPlayer
        if (url == currentUrl && loadedPlayer != null) {
            //la misma pista: si ya cargo sigue desde donde iba; si sigue cargando sonara al terminar
            if (isPrepared) startPlayback(loadedPlayer)
            return
        }

        stop()
        currentUrl = url

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                //las pistas que vienen dentro de la app (res/raw) se abren con el context
                if (url.startsWith(LOCAL_RESOURCE_SCHEME) && context != null) {
                    setDataSource(context, Uri.parse(url))
                } else {
                    setDataSource(url)
                }
                isLooping = true //musica ambiental: al terminar vuelve a empezar hasta que el usuario la pause
                setOnPreparedListener { mp ->
                    isPrepared = true
                    if (playWhenReady) startPlayback(mp)
                }
                setOnErrorListener { _, _, _ ->
                    //el reproductor con error ya no sirve: se libera para que el siguiente play cree uno nuevo
                    stop()
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
            stop()
            onPlaybackStateChanged(false)
            onError()
        }
    }

    private fun startPlayback(player: MediaPlayer) {
        try {
            player.start()
            onStateChanged(true)
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            onStateChanged(false)
        }
    }

    //pausa la reproduccion actual; si todavia estaba cargando, no empieza al terminar
    override fun pause() {
        playWhenReady = false
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    //reanuda la reproduccion pausada desde donde iba; false si no hay pista cargada
    override fun resume(): Boolean {
        val player = mediaPlayer ?: return false
        playWhenReady = true
        if (isPrepared) startPlayback(player)
        return true
    }

    //detiene por completo la reproduccion; release sirve en cualquier estado, tambien mientras carga
    fun stop() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            currentUrl = null
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
    override fun release() {
        playWhenReady = false
        onStateChanged = {}
        stop()
    }

    companion object {
        private const val LOCAL_RESOURCE_SCHEME = "android.resource://"
    }
}
