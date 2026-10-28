package com.moviles.ark.data.local.entities

import androidx.room.Entity

//tabla de conteo de visitas diarias a la pantalla de estadisticas para consolidacion en el backend
@Entity(
    tableName = "stats_daily_visits",
    primaryKeys = ["userId", "dateKey"]
)
data class StatsDailyVisitEntity(
    val userId: String,
    //fecha en formato YYYYMMDD (ej. "20261028")
    val dateKey: String,
    //cantidad de veces que el usuario entro a stats en el dia
    val count: Int = 1,
    //milisegundos del ultimo ingreso
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    //false mientras no se haya sincronizado con Firestore
    val isSynced: Boolean = false
)
