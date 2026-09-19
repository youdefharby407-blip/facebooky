package com.yousef.facebooky.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.yousef.facebooky.data.model.ChatMessage
import com.yousef.facebooky.data.model.MessageType
import com.yousef.facebooky.data.model.ReplyTarget
import com.yousef.facebooky.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class MessagesSnapshot(val messages: List<ChatMessage>, val fromCache: Boolean)

class ChatRepository(private val db: FirebaseFirestore) {

    /** rooms/main/state/chat -> clearedAt: messages older than this are hidden for everyone. */
    private val chatState = db.collection(FirebasePaths.ROOMS)
        .document(FirebasePaths.MAIN_ROOM)
        .collection(FirebasePaths.STATE)
        .document("chat")

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
     * Clears the chat for everyone. The server only accepts it with the right password:
     * the password goes into a write-only clearAuth document in the same batch, checked by the rules.
     */
    suspend fun clearForEveryone(uid: String, password: String) {
        val proof = db.collection("clearAuth").document()
        db.batch()
            .set(proof, mapOf("key" to password, "by" to uid, "at" to FieldValue.serverTimestamp()))
            .set(chatState, mapOf("clearedAt" to FieldValue.serverTimestamp(), "clearedBy" to uid, "proof" to proof.id))
            .commit()
            .await()
    }

    private val messages = db.collection(FirebasePaths.ROOMS)
        .document(FirebasePaths.MAIN_ROOM)
        .collection(FirebasePaths.MESSAGES)

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

    /**
     * Writes are not awaited: Firestore queues them offline and the message shows
     * immediately with a "sending" marker until the server confirms.
     */
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
}
