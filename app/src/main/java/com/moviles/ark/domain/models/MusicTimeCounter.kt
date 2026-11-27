package com.moviles.ark.domain.models

//cuenta cuanto tiempo sono la musica mientras el ejercicio de respiracion corria
//el viewmodel lo llama en cada paso del temporizador; las pausas del ejercicio no cuentan
class MusicTimeCounter {
    //milisegundos con musica sonando en esta sesion
    var musicMs: Long = 0L
        private set

    //momento del ultimo paso del temporizador, en milisegundos
    private var lastTickMs: Long = 0L

    //el ejercicio empieza o se reanuda: desde aqui se empieza a medir
    fun onExerciseStarted(nowMs: Long) {
        lastTickMs = nowMs
    }

    //un paso del temporizador: si la musica estaba sonando se suma el tiempo desde el paso anterior
    fun onTick(nowMs: Long, isMusicPlaying: Boolean) {
        if (isMusicPlaying) {
            musicMs += (nowMs - lastTickMs).coerceAtLeast(0L)
        }
        lastTickMs = nowMs
    }

    //segundos completos con musica
    fun musicSeconds(): Int = (musicMs / 1000L).toInt()

    //sesion nueva: vuelve a cero
    fun clear() {
        musicMs = 0L
        lastTickMs = 0L
    }
}
