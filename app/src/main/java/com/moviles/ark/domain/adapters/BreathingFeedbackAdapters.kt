package com.moviles.ark.domain.adapters

//vibracion del telefono para la respiracion; el viewmodel decide cuando y la implementacion como (data/local/sensors)
interface HapticFeedbackAdapter {
    fun vibrateExhaleCue()
}

//reproductor de la musica ambiental; la implementacion usa el MediaPlayer de android
interface AmbientAudioPlayer {
    //onError avisa si no se pudo reproducir (por ejemplo una pista de internet sin conexion)
    fun playUrl(url: String, onPlaybackStateChanged: (isPlaying: Boolean) -> Unit = {}, onError: () -> Unit = {})
    fun pause()
    fun release()
}
