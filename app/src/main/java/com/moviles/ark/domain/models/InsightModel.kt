package com.moviles.ark.domain.models

//modelo simple de un hallazgo util para la tarjeta visual (#28 y #29)
data class InsightModel(
    val title: String = "",
    val message: String = "",
    val advice: String = "",
    val actionLabel: String = ""
)
