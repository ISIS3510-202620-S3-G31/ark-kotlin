package com.moviles.ark.domain.exceptions

import com.moviles.ark.domain.models.MoodCheckInModel

//ya hay un check-in de hoy guardado en el telefono: no se guarda otro (por ejemplo dos toques rapidos sin red)
class AlreadyCheckedInTodayException : Exception(MoodCheckInModel.ALREADY_CHECKED_IN_ERROR)
