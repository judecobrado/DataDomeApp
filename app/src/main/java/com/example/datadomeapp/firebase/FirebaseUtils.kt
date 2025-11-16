package com.example.datadomeapp.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

object FirebaseUtils {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    fun currentUid(): String? = auth.currentUser?.uid

    val currentTerm: String
        get() = "1st" // Or fetch dynamically

    val currentYear: String
        get() = "2025-2026" // Or fetch dynamically

    val currentSemester: String
        get() = "Fall" // Or fetch dynamically
}
