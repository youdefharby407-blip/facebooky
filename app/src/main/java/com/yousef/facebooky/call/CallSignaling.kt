package com.yousef.facebooky.call

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.yousef.facebooky.data.FirebasePaths
import com.yousef.facebooky.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

object CallStates {
    const val RINGING = "ringing"
    const val ACCEPTED = "accepted"
    const val REJECTED = "rejected"
    const val ENDED = "ended"
}

data class CallDoc(
    val id: String,
    val callerUid: String,
    val callerName: String,
    val callerPhoto: String,
    val video: Boolean,
    val state: String,
    val offer: SessionDescription?,
    val answer: SessionDescription?,
    val calleeUid: String,
    val calleeName: String,
    val createdAtMs: Long,
)

/** Firestore-based WebRTC signaling: offer, answer, ICE candidates and call state. */
class CallSignaling(db: FirebaseFirestore) {

    private val calls = db.collection(FirebasePaths.CALLS)

    fun newCallId(): String = calls.document().id

    suspend fun createCall(id: String, caller: UserProfile, video: Boolean, offer: SessionDescription) {
        calls.document(id).set(
            mapOf(
                "callerUid" to caller.uid,
                "callerName" to caller.name,
                "callerPhoto" to caller.photoUrl,
                "video" to video,
                "state" to CallStates.RINGING,
                "offer" to offer.toMap(),
                "answer" to null,
                "calleeUid" to "",
                "calleeName" to "",
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun getCall(id: String): CallDoc? = calls.document(id).get().await().toCallDoc()

    suspend fun accept(id: String, me: UserProfile, answer: SessionDescription) {
        calls.document(id).update(
            mapOf(
                "answer" to answer.toMap(),
                "state" to CallStates.ACCEPTED,
                "calleeUid" to me.uid,
                "calleeName" to me.name,
            )
        ).await()
    }

    fun setState(id: String, state: String) {
        calls.document(id).update("state", state)
    }

    fun sendCandidate(callId: String, fromUid: String, c: IceCandidate) {
        calls.document(callId).collection(FirebasePaths.CANDIDATES).add(
            mapOf(
                "fromUid" to fromUid,
                "sdp" to c.sdp,
                "sdpMid" to (c.sdpMid ?: ""),
                "sdpMLineIndex" to c.sdpMLineIndex,
            )
        )
    }

    fun observeCall(id: String): Flow<CallDoc?> = callbackFlow {
        val reg = calls.document(id).addSnapshotListener { snap, e ->
            if (e != null) {
                close(e)
                return@addSnapshotListener
            }
            trySend(snap?.toCallDoc())
        }
        awaitClose { reg.remove() }
    }

    fun observeRinging(): Flow<List<CallDoc>> = callbackFlow {
        val reg = calls.whereEqualTo("state", CallStates.RINGING).addSnapshotListener { snap, e ->
            if (e != null) {
                close(e)
                return@addSnapshotListener
            }
            trySend(snap?.documents.orEmpty().mapNotNull { it.toCallDoc() })
        }
        awaitClose { reg.remove() }
    }

    fun observeRemoteCandidates(callId: String, myUid: String): Flow<IceCandidate> = callbackFlow {
        val reg = calls.document(callId).collection(FirebasePaths.CANDIDATES).addSnapshotListener { snap, e ->
            if (e != null) {
                close(e)
                return@addSnapshotListener
            }
            snap?.documentChanges.orEmpty()
                .filter { it.type == DocumentChange.Type.ADDED }
                .map { it.document }
                .filter { it.getString("fromUid") != myUid }
                .forEach { d ->
                    val sdp = d.getString("sdp") ?: return@forEach
                    trySend(IceCandidate(d.getString("sdpMid"), (d.getLong("sdpMLineIndex") ?: 0L).toInt(), sdp))
                }
        }
        awaitClose { reg.remove() }
    }

    private fun SessionDescription.toMap() = mapOf("type" to type.canonicalForm(), "sdp" to description)

    private fun Any?.toSdp(): SessionDescription? {
        val m = this as? Map<*, *> ?: return null
        val type = m["type"] as? String ?: return null
        val sdp = m["sdp"] as? String ?: return null
        return SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
    }

    private fun DocumentSnapshot.toCallDoc(): CallDoc? {
        if (!exists()) return null
        return CallDoc(
            id = id,
            callerUid = getString("callerUid").orEmpty(),
            callerName = getString("callerName").orEmpty(),
            callerPhoto = getString("callerPhoto").orEmpty(),
            video = getBoolean("video") ?: false,
            state = getString("state").orEmpty(),
            offer = get("offer").toSdp(),
            answer = get("answer").toSdp(),
            calleeUid = getString("calleeUid").orEmpty(),
            calleeName = getString("calleeName").orEmpty(),
            createdAtMs = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.toDate()?.time ?: 0L,
        )
    }
}
