package com.yousef.facebooky.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.yousef.facebooky.data.model.SharedPlayback
import com.yousef.facebooky.data.model.Song
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class LobbySong(val title: String, val url: String, val sizeBytes: Long)

class MusicRepository(db: FirebaseFirestore, roomId: String) {

    private val room = db.collection(FirebasePaths.ROOMS).document(roomId)
    private val songs = room.collection(FirebasePaths.MUSIC)
    private val playerDoc = room.collection(FirebasePaths.STATE).document(FirebasePaths.PLAYER_DOC)

    fun newSongId(): String = songs.document().id

    fun observeSongs(): Flow<List<Song>> = callbackFlow {
        val reg = songs.orderBy("createdAt", Query.Direction.ASCENDING).addSnapshotListener { snap, e ->
            if (e != null) {
                close(e)
                return@addSnapshotListener
            }
            val list = snap?.documents.orEmpty().map { d ->
                Song(
                    id = d.id,
                    title = d.getString("title").orEmpty().ifBlank { "Untitled" },
                    url = d.getString("url").orEmpty(),
                    storagePath = d.getString("storagePath").orEmpty(),
                    uploaderUid = d.getString("uploaderUid").orEmpty(),
                )
            }.filter { it.url.isNotBlank() }
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    fun observePlayback(): Flow<SharedPlayback?> = callbackFlow {
        val reg = playerDoc.addSnapshotListener { snap, e ->
            if (e != null) {
                close(e)
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            trySend(
                SharedPlayback(
                    songId = snap.getString("songId").orEmpty(),
                    songTitle = snap.getString("songTitle").orEmpty(),
                    songUrl = snap.getString("songUrl").orEmpty(),
                    playing = snap.getBoolean("playing") ?: false,
                    positionMs = snap.getLong("positionMs") ?: 0L,
                    updatedAtMs = snap.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.toDate()?.time ?: System.currentTimeMillis(),
                    updatedBy = snap.getString("updatedBy").orEmpty(),
                )
            )
        }
        awaitClose { reg.remove() }
    }

    /** One-shot fetch of the shared lobby's songs, to let a user copy some into a private room. */
    suspend fun fetchLobbySongs(db: FirebaseFirestore): List<LobbySong> {
        val snap = db.collection(FirebasePaths.ROOMS).document(FirebasePaths.MAIN_ROOM)
            .collection(FirebasePaths.MUSIC).get().await()
        return snap.documents.mapNotNull { d ->
            val url = d.getString("url") ?: return@mapNotNull null
            LobbySong(d.getString("title").orEmpty(), url, d.getLong("sizeBytes") ?: 0L)
        }
    }

    suspend fun addSong(id: String, title: String, url: String, storagePath: String, uploaderUid: String, sizeBytes: Long) {
        songs.document(id).set(
            mapOf(
                "title" to title,
                "url" to url,
                "storagePath" to storagePath,
                "uploaderUid" to uploaderUid,
                "sizeBytes" to sizeBytes,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    /** Deletes a song. The bundled default song ("معاك قلبي") can never be deleted (guarded in the UI + rules). */
    fun deleteSong(id: String, onError: (Exception) -> Unit) {
        songs.document(id).delete().addOnFailureListener(onError)
    }

    fun publish(state: SharedPlayback) {
        playerDoc.set(
            mapOf(
                "songId" to state.songId,
                "songTitle" to state.songTitle,
                "songUrl" to state.songUrl,
                "playing" to state.playing,
                "positionMs" to state.positionMs,
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedBy" to state.updatedBy,
            )
        )
    }
}
