package com.moviles.ark.domain.adapters

import com.moviles.ark.domain.models.WeatherModel

//adapter del servicio de clima: el home pide el clima con esta interfaz y no sabe que api hay detras
//hoy la implementacion es open-meteo; cambiar de api es escribir otra clase que cumpla esto
interface WeatherServiceAdapter {
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): Result<WeatherModel>

    //ultimo clima guardado si todavia vale; sirve cuando no hay ubicacion. null si no hay o ya vencio
    fun getCachedWeather(): WeatherModel? = null
}
