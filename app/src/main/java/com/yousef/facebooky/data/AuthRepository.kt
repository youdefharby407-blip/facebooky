package com.yousef.facebooky.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/** Silent anonymous sign-in. The UID is never shown to the user. */
class AuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: error("Anonymous sign-in returned no user")
    }
}
