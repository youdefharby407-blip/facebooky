package com.yousef.facebooky.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.yousef.facebooky.data.model.Presence
import com.yousef.facebooky.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Admin console + presence.
 *
 * The admin password is NEVER stored in the app. To become admin, the user submits it and the
 * server checks its SHA-256 (same trick as clear-chat), then marks users/{uid}.admin = true.
 * Admin can then read every user and every room (the rules allow reads for admins).
 */
class AdminRepository(private val db: FirebaseFirestore) {

    /** Heartbeat: records that this device is online now. Best-effort. */
    fun heartbeat(uid: String, device: String) {
        db.collection(FirebasePaths.USERS).document(uid).set(
            mapOf(
                "lastSeen" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "device" to device,
            ),
            SetOptions.merge(),
        )
    }

    /**
     * Verifies the admin password and, if correct, flags this user as admin.
     * Works because the rules only let users/{uid}.admin become true alongside a valid clearAuth proof.
     */
    suspend fun becomeAdmin(uid: String, password: String) {
        val proof = db.collection(FirebasePaths.CLEAR_AUTH).document()
        db.batch()
            .set(proof, mapOf("key" to password, "by" to uid, "at" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
            .set(
                db.collection(FirebasePaths.USERS).document(uid),
                mapOf("admin" to true, "adminProof" to proof.id),
                SetOptions.merge(),
            )
            .commit()
            .await()
    }

    /** ADMIN: every user/device known to the project. */
    fun observeAllUsers(): Flow<List<Presence>> = callbackFlow {
        val reg = db.collection(FirebasePaths.USERS).addSnapshotListener { snap, e ->
            if (e != null) {
                close(e); return@addSnapshotListener
            }
            trySend(
                snap?.documents.orEmpty().map { d ->
                    Presence(
                        uid = d.id,
                        name = d.getString("name").orEmpty(),
                        photoUrl = d.getString("photoUrl").orEmpty(),
                        shortId = d.getString("shortId").orEmpty(),
                        lastSeenMs = d.getTimestamp("lastSeen")?.toDate()?.time ?: 0L,
                        device = d.getString("device").orEmpty(),
                    )
                }
            )
        }
        awaitClose { reg.remove() }
    }
}
