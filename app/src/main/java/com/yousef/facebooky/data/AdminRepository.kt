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
    fun heartbeat(uid: String, device: String, region: String, language: String) {
        db.collection(FirebasePaths.USERS).document(uid).set(
            mapOf(
                "lastSeen" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "device" to device,
                "region" to region,
                "language" to language,
            ),
            SetOptions.merge(),
        )
    }

    private val adminDoc = db.collection("config").document("admin")

    /**
     * Verifies the admin password. If correct, this device becomes THE admin (single device):
     * config/admin.uid is set to me, which automatically demotes any previous admin device.
     * The rainbow tag / admin powers key off "am I config/admin.uid".
     */
    suspend fun becomeAdmin(uid: String, password: String) {
        val proof = db.collection(FirebasePaths.CLEAR_AUTH).document()
        db.batch()
            .set(proof, mapOf("key" to password, "by" to uid, "at" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
            .set(adminDoc, mapOf("uid" to uid, "proof" to proof.id,
                "at" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
            .commit()
            .await()
    }

    /** Live: which single uid is currently the admin ("" if none). */
    fun observeAdminUid(): Flow<String> = callbackFlow {
        val reg = adminDoc.addSnapshotListener { snap, e ->
            if (e != null) { close(e); return@addSnapshotListener }
            trySend(snap?.getString("uid").orEmpty())
        }
        awaitClose { reg.remove() }
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
                        region = d.getString("region").orEmpty(),
                        language = d.getString("language").orEmpty(),
                    )
                }
            )
        }
        awaitClose { reg.remove() }
    }
}
