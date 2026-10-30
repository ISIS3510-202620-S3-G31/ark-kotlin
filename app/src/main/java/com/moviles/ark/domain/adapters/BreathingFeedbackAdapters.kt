package com.moviles.ark.domain.adapters

import com.moviles.ark.domain.models.BreathingPhase

//vibracion del telefono para la respiracion; el viewmodel decide cuando y la implementacion como (data/local/sensors)
interface HapticFeedbackAdapter {
    //cada etapa del ciclo tiene su vibracion para seguir el ejercicio con los ojos cerrados
    fun vibratePhaseCue(phase: BreathingPhase)
}

//reproductor de la musica ambiental; la implementacion usa el MediaPlayer de android
interface AmbientAudioPlayer {
    //prepara la url (archivo en cache, res/raw o streaming) y la reproduce en bucle
    //onPlaybackStateChanged avisa cuando de verdad empieza a sonar o se detiene
    //onError avisa si no se pudo reproducir (por ejemplo una pista de internet sin conexion)
    fun playUrl(url: String, onPlaybackStateChanged: (isPlaying: Boolean) -> Unit = {}, onError: () -> Unit = {})
    //pausa; si la pista todavia estaba cargando ya no empieza a sonar sola al terminar de cargar
    fun pause()
    //reanuda la pista cargada desde donde iba; false si no hay ninguna cargada
    fun resume(): Boolean
    fun release()
}
