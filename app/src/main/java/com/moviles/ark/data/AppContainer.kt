package com.moviles.ark.data

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.moviles.ark.data.local.sensors.Location
import com.moviles.ark.data.repositories.AuthRepositoryImpl
import com.moviles.ark.data.repositories.LocationRepositoryImpl
import com.moviles.ark.data.repositories.MoodRepositoryImpl
import com.moviles.ark.domain.repositories.AuthRepository
import com.moviles.ark.domain.repositories.LocationRepository
import com.moviles.ark.domain.repositories.MoodRepository

interface AppContainer {
    val auth: FirebaseAuth
    val firestore: FirebaseFirestore
    val authRepository: AuthRepository
    val locationRepository: LocationRepository
    val moodRepository: MoodRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val auth: FirebaseAuth by lazy { Firebase.auth }
    override val firestore: FirebaseFirestore by lazy { Firebase.firestore }
    override val authRepository: AuthRepository by lazy { AuthRepositoryImpl(auth, firestore) }
    override val locationRepository: LocationRepository by lazy { LocationRepositoryImpl(Location(context)) }
    override val moodRepository: MoodRepository by lazy { MoodRepositoryImpl(auth, firestore) }
}
