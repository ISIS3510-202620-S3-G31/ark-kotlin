package com.moviles.ark.data

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.moviles.ark.data.local.sensors.Location
import com.moviles.ark.data.repositories.AuthRepositoryImpl
import com.moviles.ark.data.repositories.FakeToolRepository
import com.moviles.ark.data.repositories.LocationRepositoryImpl
import com.moviles.ark.data.repositories.MoodRepositoryImpl
import com.moviles.ark.domain.repositories.AuthRepository
import com.moviles.ark.domain.repositories.LocationRepository
import com.moviles.ark.domain.repositories.MoodRepository
import com.moviles.ark.domain.repositories.PhotoRepository
import com.moviles.ark.data.repositories.FakePhotoRepository
import com.moviles.ark.data.repositories.FakeToolRepository
import com.moviles.ark.domain.repositories.ToolRepository

interface AppContainer {
    val auth: FirebaseAuth
    val firestore: FirebaseFirestore
    val authRepository: AuthRepository
    val locationRepository: LocationRepository
    val moodRepository: MoodRepository
    val toolRepository: ToolRepository
    val photoRepository: PhotoRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val auth: FirebaseAuth by lazy { Firebase.auth }
    override val firestore: FirebaseFirestore by lazy { Firebase.firestore }
    override val authRepository: AuthRepository by lazy { AuthRepositoryImpl(auth, firestore) }
    override val locationRepository: LocationRepository by lazy { LocationRepositoryImpl(Location(context)) }
    override val moodRepository: MoodRepository by lazy { MoodRepositoryImpl(auth, firestore) }
    // fake catalog until the Room implementation of #9 is ready; then only this line changes
    override val toolRepository: ToolRepository by lazy { FakeToolRepository() }
    // fake photos in memory until #26 saves them in Room and filesDir; then only this line changes
    override val photoRepository: PhotoRepository by lazy { FakePhotoRepository() }
}
