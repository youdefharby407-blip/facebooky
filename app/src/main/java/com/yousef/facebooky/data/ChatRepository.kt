package com.yousef.facebooky.data

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.yousef.facebooky.data.model.ChatMessage
import com.yousef.facebooky.data.model.MessageType
import com.yousef.facebooky.data.model.ReplyTarget
import com.yousef.facebooky.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class MessagesSnapshot(val messages: List<ChatMessage>, val fromCache: Boolean)

/**
 * Reads and writes the messages of ONE room. A new instance is created whenever the
 * user switches room, so all paths below are scoped to [roomId].
 */
class ChatRepository(private val db: FirebaseFirestore, val roomId: String) {

    private val room = db.collection(FirebasePaths.ROOMS).document(roomId)
    private val messages: CollectionReference = room.collection(FirebasePaths.MESSAGES)
    private val chatState = room.collection(FirebasePaths.STATE).document(FirebasePaths.CHAT_STATE_DOC)
    private val presence = room.collection("presence")

    fun newId(): String = messages.document().id

    /** Real-time stream of the latest messages, oldest first. */
    fun observe(limit: Long = 300): Flow<MessagesSnapshot> = callbackFlow {
        val registration = messages
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val list = snap.documents.map { it.toMessage() }.reversed()
                trySend(MessagesSnapshot(list, snap.metadata.isFromCache))
            }
        awaitClose { registration.remove() }
    }

    fun observeClearedAt(): Flow<Long> = callbackFlow {
        val reg = chatState.addSnapshotListener { snap, e ->
            if (e != null) {
                close(e)
                return@addSnapshotListener
            }
            trySend(
                snap?.getTimestamp("clearedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.toDate()?.time ?: 0L
            )
        }
        awaitClose { reg.remove() }
    }

    /**
     * Clears the chat for everyone. Accepted by the server only with the right password:
     * the password goes into a write-only clearAuth doc in the same batch, checked by the rules.
     */
    suspend fun clearForEveryone(uid: String, password: String) {
        val proof = db.collection(FirebasePaths.CLEAR_AUTH).document()
        db.batch()
            .set(proof, mapOf("key" to password, "by" to uid, "at" to FieldValue.serverTimestamp()))
            .set(chatState, mapOf("clearedAt" to FieldValue.serverTimestamp(), "clearedBy" to uid, "proof" to proof.id))
            .commit()
            .await()
    }

    fun send(
        id: String,
        sender: UserProfile,
        type: String,
        text: String = "",
        mediaUrl: String = "",
        mediaPath: String = "",
        refId: String = "",
        durationMs: Long = 0L,
        reply: ReplyTarget? = null,
        onError: (Exception) -> Unit = {},
    ) {
        messages.document(id).set(
            mapOf(
                "senderUid" to sender.uid,
                "senderName" to sender.name,
                "senderPhoto" to sender.photoUrl,
                "type" to type,
                "text" to text,
                "mediaUrl" to mediaUrl,
                "mediaPath" to mediaPath,
                "refId" to refId,
                "durationMs" to durationMs,
                "replyToId" to (reply?.id ?: ""),
                "replyToName" to (reply?.name ?: "").take(40),
                "replyToText" to (reply?.text ?: "").take(200),
                "timestamp" to FieldValue.serverTimestamp(),
            )
        ).addOnFailureListener(onError)
        // Bump room activity so it sorts to the top of the room list (best-effort).
        room.set(mapOf("lastActivity" to FieldValue.serverTimestamp()), SetOptions.merge())
    }

    private fun DocumentSnapshot.toMessage() = ChatMessage(
        id = id,
        senderUid = getString("senderUid").orEmpty(),
        senderName = getString("senderName").orEmpty(),
        senderPhoto = getString("senderPhoto").orEmpty(),
        type = getString("type").orEmpty(),
        text = getString("text").orEmpty(),
        mediaUrl = getString("mediaUrl").orEmpty(),
        mediaPath = getString("mediaPath").orEmpty(),
        refId = getString("refId").orEmpty(),
        durationMs = getLong("durationMs") ?: 0L,
        timestamp = getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate(),
        pending = metadata.hasPendingWrites(),
        replyToId = getString("replyToId").orEmpty(),
        replyToName = getString("replyToName").orEmpty(),
        replyToText = getString("replyToText").orEmpty(),
        reactions = (get("reactions") as? Map<*, *>).orEmpty()
            .mapNotNull { (k, v) -> if (k is String && v is String && v.isNotBlank()) k to v else null }
            .toMap(),
        edited = getBoolean("edited") ?: false,
    )

    /** Sets (or with null, removes) my reaction. Only my own entry is ever written. */
    fun react(messageId: String, uid: String, emoji: String?, onError: (Exception) -> Unit) {
        messages.document(messageId)
            .update(FieldPath.of("reactions", uid), emoji ?: FieldValue.delete())
            .addOnFailureListener(onError)
    }

    /** Removes the content for everyone and leaves a "message deleted" placeholder. */
    fun deleteForEveryone(messageId: String, onError: (Exception) -> Unit) {
        messages.document(messageId).update(
            mapOf(
                "type" to MessageType.DELETED,
                "text" to "",
                "mediaUrl" to "",
                "replyToText" to "",
                "reactions" to emptyMap<String, String>(),
            )
        ).addOnFailureListener(onError)
    }

    /** Edits my own text message (adds edited=true). */
    fun editText(messageId: String, newText: String, onError: (Exception) -> Unit) {
        messages.document(messageId).update(mapOf("text" to newText.take(4000), "edited" to true))
            .addOnFailureListener(onError)
    }

    // ---------- per-room presence: typing + last read (seen) ----------

    /** Marks that I'm typing (or not) and updates my last-read time. Best-effort. */
    fun setTyping(uid: String, typing: Boolean) {
        presence.document(uid).set(
            mapOf("typing" to typing, "at" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        )
    }

    /** Marks the whole chat as read up to now (called when I'm looking at it). */
    fun markRead(uid: String) {
        presence.document(uid).set(
            mapOf("lastReadMs" to FieldValue.serverTimestamp(), "at" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        )
    }

    data class RoomPresence(val typing: Set<String>, val lastReadMs: Map<String, Long>)

    /** Live presence of everyone in the room (who's typing, who read up to when). */
    fun observePresence(): Flow<RoomPresence> = callbackFlow {
        val reg = presence.addSnapshotListener { snap, e ->
            if (e != null) { close(e); return@addSnapshotListener }
            val docs = snap?.documents.orEmpty()
            val now = System.currentTimeMillis()
            val typing = docs.filter {
                (it.getBoolean("typing") ?: false) &&
                    ((it.getTimestamp("at")?.toDate()?.time ?: 0L) > now - 8000) // typing expires after 8s
            }.map { it.id }.toSet()
            val read = docs.associate { it.id to (it.getTimestamp("lastReadMs")?.toDate()?.time ?: 0L) }
            trySend(RoomPresence(typing, read))
        }
        awaitClose { reg.remove() }
    }
}
