package com.yousef.facebooky.data

/**
 * Firestore layout (multi-room)
 *   users/{uid}                                  profile (name, photoUrl, shortId, presence, admin)
 *   shortIds/{shortId}                           -> uid (public directory to start a chat by ID)
 *   rooms/{roomId}                               room metadata (members, theme, createdAt)
 *   rooms/{roomId}/chat/{messageId}              chat messages
 *   rooms/{roomId}/music/{songId}                songs added in this room
 *   rooms/{roomId}/state/player                  shared music playback
 *   rooms/{roomId}/state/chat                    clearedAt (clear-for-everyone marker)
 *   calls/{callId}                               WebRTC signaling
 *   calls/{callId}/candidates/{candidateId}      ICE candidates
 *   blobs/{blobId}(+/chunks/{i})                 media stored in Firestore (see BlobStore)
 *   clearAuth/{proof}                            write-only admin-password proof
 *   epoch/current                                bump to wipe everything and start fresh
 *
 * roomId for a 1:1 chat = "p_" + the two uids sorted and joined by "_".
 * "main" stays as the shared lobby every profile can read.
 */
object FirebasePaths {
    const val USERS = "users"
    const val SHORT_IDS = "shortIds"
    const val ROOMS = "rooms"
    const val MAIN_ROOM = "main"
    const val MESSAGES = "chat"
    const val MUSIC = "music"
    const val STATE = "state"
    const val PLAYER_DOC = "player"
    const val CHAT_STATE_DOC = "chat"
    const val CALLS = "calls"
    const val CANDIDATES = "candidates"
    const val BLOBS = "blobs"
    const val CHUNKS = "chunks"
    const val CLEAR_AUTH = "clearAuth"
    const val EPOCH = "epoch"

    /** Deterministic room id for a private 1:1 chat between two uids. */
    fun roomFor(a: String, b: String): String =
        if (a == b) "self_$a" else "p_" + listOf(a, b).sorted().joinToString("_")
}
