package com.moviles.ark.data

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

// Shared dependencies for the whole app. ViewModels get what they need from here
// instead of creating their own instances.
// The Room database (#11) and the repositories (#4, #9) will be added here.
interface AppContainer {
    val auth: FirebaseAuth
    val firestore: FirebaseFirestore
}

class DefaultAppContainer : AppContainer {
    // lazy: each instance is created the first time someone uses it, not at app start
    override val auth: FirebaseAuth by lazy { Firebase.auth }
    override val firestore: FirebaseFirestore by lazy { Firebase.firestore }
}
