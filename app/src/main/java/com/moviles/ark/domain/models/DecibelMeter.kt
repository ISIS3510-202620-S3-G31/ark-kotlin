package com.moviles.ark.domain.models

import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

//una lectura del microfono del scream tank (#157)
data class DecibelReading(
    //raiz del promedio de los cuadrados de las muestras (0 a 32768)
    val rms: Double,
    //decibeles respecto al maximo del microfono: 0 es lo mas fuerte, MIN_DBFS es silencio
    val dbfs: Double,
    //el mismo volumen en una escala de 0 a 100 facil de leer
    val level: Int
)

//convierte un pedazo de audio pcm de 16 bits en volumen (#157)
//es kotlin puro: no sabe nada de android ni del microfono, solo recibe numeros que ya estan en memoria
object DecibelMeter {
    //valor absoluto mas grande de una muestra de 16 bits (Short.MIN_VALUE)
    const val FULL_SCALE = 32768.0

    //por debajo de esto se toma como silencio; un microfono de celular en un cuarto callado anda por -60 dbfs
    const val MIN_DBFS = -60.0

    const val MAX_LEVEL = 100

    //rms de las primeras count muestras; el buffer se reutiliza, por eso no se usa su tamano completo
    fun rms(samples: ShortArray, count: Int = samples.size): Double {
        val n = count.coerceIn(0, samples.size)
        if (n == 0) return 0.0
        var sumOfSquares = 0.0
        for (i in 0 until n) {
            val sample = samples[i].toDouble()
            sumOfSquares += sample * sample
        }
        return sqrt(sumOfSquares / n)
    }

    //dbfs = 20 * log10(rms / maximo); el silencio total daria menos infinito, por eso se corta en MIN_DBFS
    fun toDbfs(rms: Double): Double {
        if (rms <= 0.0) return MIN_DBFS
        return (20.0 * log10(rms / FULL_SCALE)).coerceIn(MIN_DBFS, 0.0)
    }

    //pasa de MIN_DBFS..0 a 0..100 en linea recta
    fun toLevel(dbfs: Double): Int {
        val fraction = (dbfs - MIN_DBFS) / -MIN_DBFS
        return (fraction * MAX_LEVEL).roundToInt().coerceIn(0, MAX_LEVEL)
    }

    fun measure(samples: ShortArray, count: Int = samples.size): DecibelReading {
        val rms = rms(samples, count)
        val dbfs = toDbfs(rms)
        return DecibelReading(rms = rms, dbfs = dbfs, level = toLevel(dbfs))
    }
}
