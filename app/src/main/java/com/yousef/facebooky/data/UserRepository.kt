package com.yousef.facebooky.data

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.yousef.facebooky.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * The profile is saved on the phone first (name + photo file), so it is available
 * instantly and offline, then synced to Firestore/Storage in the background.
 */
class UserRepository(private val db: FirebaseFirestore, private val context: Context) {

    private val prefs = context.getSharedPreferences("profile_cache", Context.MODE_PRIVATE)

    /** Local profile for [uid]. The name/photo are kept even if the anonymous UID changed. */
    fun cached(uid: String): UserProfile? {
        val name = prefs.getString("name", "").orEmpty()
        if (name.isBlank()) return null
        val local = prefs.getString("localPhoto", "").orEmpty().takeIf { it.isNotBlank() && File(it).exists() }.orEmpty()
        // A remote photo URL is only reusable if it belongs to this UID.
        val remote = if (prefs.getString("uid", null) == uid) prefs.getString("photo", "").orEmpty() else ""
        return UserProfile(uid, name, remote, local).takeIf { it.isComplete }
    }

    fun needsSync(uid: String): Boolean =
        prefs.getString("name", "").orEmpty().isNotBlank() &&
            (prefs.getString("uid", null) != uid || prefs.getBoolean("dirty", true))

    /** Saves name and (optionally) a new photo on the phone. Never touches the network. */
    fun saveLocal(uid: String, name: String, newPhotoJpeg: ByteArray?): UserProfile {
        var local = prefs.getString("localPhoto", "").orEmpty()
        var remote = if (prefs.getString("uid", null) == uid) prefs.getString("photo", "").orEmpty() else ""
        if (newPhotoJpeg != null) {
            val file = File(context.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
            file.writeBytes(newPhotoJpeg)
            if (local.isNotBlank() && local != file.absolutePath) File(local).delete()
            local = file.absolutePath
            remote = "" // must be uploaded again
        }
        prefs.edit()
            .putString("uid", uid)
            .putString("name", name)
            .putString("photo", remote)
            .putString("localPhoto", local)
            .putBoolean("dirty", true)
            .apply()
        return UserProfile(uid, name, remote, local)
    }

    fun setRemotePhoto(uid: String, url: String) {
        if (prefs.getString("uid", null) == uid) prefs.edit().putString("photo", url).apply()
    }

    /** Everyone's current name + photo (uid -> profile), live. Used for avatars and names in the chat. */
    fun observeAll(): Flow<Map<String, UserProfile>> = callbackFlow {
        val reg = db.collection(FirebasePaths.USERS).addSnapshotListener { snap, e ->
            if (e != null) {
                close(e)
                return@addSnapshotListener
            }
            trySend(
                snap?.documents.orEmpty().associate { d ->
                    d.id to UserProfile(
                        d.id, d.getString("name").orEmpty(), d.getString("photoUrl").orEmpty(),
                        shortId = d.getString("shortId").orEmpty(),
                        isAdmin = d.getBoolean("admin") ?: false,
                    )
                }
            )
        }
        awaitClose { reg.remove() }
    }

    /** Profile stored in Firestore (used when this phone has no local profile yet). */
    suspend fun fetch(uid: String): UserProfile? {
        val doc = db.collection(FirebasePaths.USERS).document(uid).get().await()
        if (!doc.exists()) return null
        val profile = UserProfile(uid, doc.getString("name").orEmpty(), doc.getString("photoUrl").orEmpty(),
            shortId = doc.getString("shortId").orEmpty(), isAdmin = doc.getBoolean("admin") ?: false)
        if (!profile.isComplete) return null
        prefs.edit().putString("uid", uid).putString("name", profile.name)
            .putString("photo", profile.photoUrl).putBoolean("dirty", false).apply()
        return profile
    }

    /** Writes the profile to Firestore. [complete] = photo uploaded too, nothing left to sync. */
    suspend fun push(profile: UserProfile, complete: Boolean) {
        db.collection(FirebasePaths.USERS).document(profile.uid).set(
            mapOf(
                "name" to profile.name,
                "photoUrl" to profile.photoUrl,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        if (complete && prefs.getString("uid", null) == profile.uid && prefs.getString("name", "") == profile.name) {
            prefs.edit().putBoolean("dirty", false).apply()
        }
    }
}
