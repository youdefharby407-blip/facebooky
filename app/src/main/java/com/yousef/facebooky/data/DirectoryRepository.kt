package com.yousef.facebooky.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.yousef.facebooky.data.model.RoomInfo
import com.yousef.facebooky.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/** Short IDs, the room list, and starting a chat by ID. */
class DirectoryRepository(private val db: FirebaseFirestore) {

    /** A friendly ID like "K7Q-4M2" (no confusable chars). */
    private fun randomShortId(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        fun part(n: Int) = (1..n).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
        return "${part(3)}-${part(3)}"
    }

    /** Ensures this user has a unique short ID, creating one if needed. Returns it. */
    suspend fun ensureShortId(uid: String, existing: String?): String {
        if (!existing.isNullOrBlank()) return existing
        repeat(6) {
            val candidate = randomShortId()
            val ref = db.collection(FirebasePaths.SHORT_IDS).document(candidate)
            val ok = runCatching {
                ref.set(mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp())).await()
            }.isSuccess
            if (ok) {
                db.collection(FirebasePaths.USERS).document(uid)
                    .set(mapOf("shortId" to candidate), com.google.firebase.firestore.SetOptions.merge()).await()
                return candidate
            }
        }
        error("Couldn't reserve an ID")
    }

    /** Resolves someone else's short ID to their uid + profile. Null if not found. */
    suspend fun lookup(shortId: String): UserProfile? {
        val id = shortId.trim().uppercase()
        val map = db.collection(FirebasePaths.SHORT_IDS).document(id).get().await()
        val uid = map.getString("uid") ?: return null
        val doc = db.collection(FirebasePaths.USERS).document(uid).get().await()
        return UserProfile(uid, doc.getString("name").orEmpty(), doc.getString("photoUrl").orEmpty(),
            shortId = id).takeIf { it.name.isNotBlank() }
    }

    /** Creates (or updates) the room document for a 1:1 chat and returns its id. */
    suspend fun openRoom(me: UserProfile, other: UserProfile): String {
        val roomId = FirebasePaths.roomFor(me.uid, other.uid)
        db.collection(FirebasePaths.ROOMS).document(roomId).set(
            mapOf(
                "members" to listOf(me.uid, other.uid).sorted(),
                "createdAt" to FieldValue.serverTimestamp(),
                "lastActivity" to FieldValue.serverTimestamp(),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
        return roomId
    }

    /** Rooms this user belongs to, newest activity first. */
    fun observeMyRooms(uid: String): Flow<List<RoomInfo>> = callbackFlow {
        val reg = db.collection(FirebasePaths.ROOMS)
            .whereArrayContains("members", uid)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    close(e); return@addSnapshotListener
                }
                trySend(snap?.documents.orEmpty().map { RoomInfo.from(it.id, it.data.orEmpty()) })
            }
        awaitClose { reg.remove() }
    }

    /** ADMIN: every room in the project. */
    fun observeAllRooms(): Flow<List<RoomInfo>> = callbackFlow {
        val reg = db.collection(FirebasePaths.ROOMS).addSnapshotListener { snap, e ->
            if (e != null) {
                close(e); return@addSnapshotListener
            }
            trySend(snap?.documents.orEmpty().map { RoomInfo.from(it.id, it.data.orEmpty()) })
        }
        awaitClose { reg.remove() }
    }

    fun setRoomTheme(roomId: String, themeId: String, onError: (Exception) -> Unit) {
        db.collection(FirebasePaths.ROOMS).document(roomId)
            .set(mapOf("theme" to themeId), com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener(onError)
    }
}
