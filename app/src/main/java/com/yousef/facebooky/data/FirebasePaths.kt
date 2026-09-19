package com.yousef.facebooky.data

/**
 * Firestore layout
 *   users/{uid}                                  profile (name, photoUrl)
 *   rooms/main/messages/{messageId}              chat messages
 *   rooms/main/music/{songId}                    uploaded songs
 *   rooms/main/state/player                      shared music playback state
 *   calls/{callId}                               WebRTC signaling (offer/answer/state)
 *   calls/{callId}/candidates/{candidateId}      ICE candidates
 *   blobs/{blobId}                               media file metadata (see BlobStore)
 *   blobs/{blobId}/chunks/{i}                    media bytes, split in <= 900 KB parts
 *
 * No Cloud Storage bucket is used: all media lives in Firestore (free Spark plan).
 */
object FirebasePaths {
    const val USERS = "users"
    const val ROOMS = "rooms"
    const val MAIN_ROOM = "main"
    const val MESSAGES = "messages"
    const val MUSIC = "music"
    const val STATE = "state"
    const val PLAYER_DOC = "player"
    const val CALLS = "calls"
    const val CANDIDATES = "candidates"
    const val BLOBS = "blobs"
    const val CHUNKS = "chunks"
}
