package com.moviles.ark.data.local.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.models.DayPeriod
import com.moviles.ark.domain.models.EnvironmentalContext
import com.moviles.ark.domain.models.LightLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

//escucha el sensor de luz del telefono (Sensor.TYPE_LIGHT) y lo junta con la hora del dia
//expone el entorno en un StateFlow para que el motor de recomendaciones reaccione solo, sin que el usuario toque nada
class EnvironmentalSensorManager(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    //se puede cambiar para fijar la hora
    private val currentHour: () -> Int = { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
) {
    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    val hasLightSensor: Boolean get() = lightSensor != null

    //el sensor solo esta registrado mientras alguien escucha (y 5 s mas por si la pantalla rota), para no gastar bateria
    val environment: StateFlow<EnvironmentalContext> =
        combine(observeLightLevel(), observeDayPeriod()) { light, period ->
            EnvironmentalContext(
                lightLevel = light?.second,
                lux = light?.first,
                dayPeriod = period,
                hasLightSensor = hasLightSensor
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = EnvironmentalContext(dayPeriod = DayPeriod.fromHour(currentHour()), hasLightSensor = hasLightSensor)
        )

    //lux y nivel; el sensor manda muchas lecturas por segundo, asi que solo pasa cuando cambia el nivel
    //sin sensor emite null una vez y el contexto se queda solo con la hora
    private fun observeLightLevel(): Flow<Pair<Float, LightLevel>?> {
        val sensor = lightSensor ?: return flow { emit(null) }
        return callbackFlow<Pair<Float, LightLevel>?> {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val lux = event.values.firstOrNull() ?: return
                    trySend(lux to LightLevel.fromLux(lux))
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            awaitClose { sensorManager.unregisterListener(listener) }
        }.distinctUntilChangedBy { it?.second }
            .catch { e ->
                CrashlyticsHelper.logNonFatal(
                    componentName = "EnvironmentalSensorManager",
                    action = "observeLightLevel",
                    throwable = e
                )
                emit(null)
            }
    }

    //la hora cambia aunque la luz no cambie, asi que se revisa cada minuto
    private fun observeDayPeriod(): Flow<DayPeriod> = flow {
        while (true) {
            emit(DayPeriod.fromHour(currentHour()))
            delay(PERIOD_CHECK_MILLIS)
        }
    }.distinctUntilChanged()

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val PERIOD_CHECK_MILLIS = 60_000L
    }
}
