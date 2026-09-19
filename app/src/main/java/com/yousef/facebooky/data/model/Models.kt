package com.yousef.facebooky.data.model

import java.util.Date

object MessageType {
    const val TEXT = "text"
    const val IMAGE = "image"
    const val VOICE = "voice"
    const val STICKER = "sticker"
    const val MUSIC = "music"
}

data class UserProfile(
    val uid: String,
    val name: String,
    /** Download URL in Firebase Storage ("" until the photo has been uploaded). */
    val photoUrl: String,
    /** Path of the photo saved on this phone, so the profile works instantly and offline. */
    val localPhoto: String = "",
) {
    val isComplete: Boolean get() = name.isNotBlank() && (photoUrl.isNotBlank() || localPhoto.isNotBlank())

    /** What to show in the UI: the local file if we have it, else the remote URL. */
    val displayPhoto: String get() = if (localPhoto.isNotBlank()) "file://$localPhoto" else photoUrl
}

data class ChatMessage(
    val id: String,
    val senderUid: String,
    val senderName: String,
    val senderPhoto: String,
    val type: String,
    val text: String,
    val mediaUrl: String,
    val mediaPath: String,
    val refId: String,
    val durationMs: Long,
    val timestamp: Date?,
    val pending: Boolean,
)

data class Song(
    val id: String,
    val title: String,
    val url: String = "",
    val storagePath: String = "",
    val uploaderUid: String = "",
    val isBundled: Boolean = false,
)

/** Shared playback state stored in rooms/main/state/player. */
data class SharedPlayback(
    val songId: String,
    val songTitle: String,
    val songUrl: String,
    val playing: Boolean,
    val positionMs: Long,
    val updatedAtMs: Long,
    val updatedBy: String,
)

enum class ConnectionStatus { CONNECTING, CONNECTED, OFFLINE }
