package com.moviles.ark.data.local

import androidx.room.TypeConverter
import org.json.JSONArray
import java.util.Date

//room solo sabe guardar tipos simples (numeros, texto, booleanos)
//estos conversores le ensenan a guardar listas de texto y fechas
class Converters {

    //["HAPPINESS","SADNESS"] se guarda como el texto json ["HAPPINESS","SADNESS"]
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return JSONArray(value).toString()
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val array = JSONArray(value)
        return List(array.length()) { index -> array.getString(index) }
    }

    //una fecha se guarda como milisegundos
    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun toDate(millis: Long?): Date? {
        return millis?.let { Date(it) }
    }
}
