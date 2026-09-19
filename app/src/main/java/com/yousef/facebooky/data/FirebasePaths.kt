package com.yousef.facebooky.data

/**
 * Firestore layout
 *   users/{uid}                                  profile (name, photoUrl)
 *   rooms/main/messages/{messageId}              chat messages
 *   rooms/main/music/{songId}                    uploaded songs
 *   rooms/main/state/player                      shared music playback state
 *   calls/{callId}                               WebRTC signaling (offer/answer/state)
 *   calls/{callId}/candidates/{candidateId}      ICE candidates
 *
 * Storage layout
 *   media/{uid}/{kind}/{file}   kind = profile | images | stickers | voice | music
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

    fun storagePath(uid: String, kind: String, fileName: String) = "media/$uid/$kind/$fileName"
}
