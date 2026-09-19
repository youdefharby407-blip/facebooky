package com.yousef.facebooky.data

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.yousef.facebooky.data.model.UserProfile
import kotlinx.coroutines.tasks.await

class UserRepository(private val db: FirebaseFirestore, context: Context) {

    // Small local cache so the profile is known instantly on start / offline. Firestore stays the source of truth.
    private val prefs = context.getSharedPreferences("profile_cache", Context.MODE_PRIVATE)

    fun cached(uid: String): UserProfile? {
        if (prefs.getString("uid", null) != uid) return null
        val profile = UserProfile(uid, prefs.getString("name", "").orEmpty(), prefs.getString("photo", "").orEmpty())
        return profile.takeIf { it.isComplete }
    }

    suspend fun fetch(uid: String): UserProfile? {
        val doc = db.collection(FirebasePaths.USERS).document(uid).get().await()
        if (!doc.exists()) return null
        val profile = UserProfile(uid, doc.getString("name").orEmpty(), doc.getString("photoUrl").orEmpty())
        if (!profile.isComplete) return null
        cache(profile)
        return profile
    }

    suspend fun save(profile: UserProfile) {
        db.collection(FirebasePaths.USERS).document(profile.uid).set(
            mapOf(
                "name" to profile.name,
                "photoUrl" to profile.photoUrl,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        cache(profile)
    }

    private fun cache(p: UserProfile) {
        prefs.edit().putString("uid", p.uid).putString("name", p.name).putString("photo", p.photoUrl).apply()
    }
}
