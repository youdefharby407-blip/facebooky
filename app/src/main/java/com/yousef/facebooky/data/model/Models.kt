package com.yousef.facebooky.data.model

import java.util.Date

object MessageType {
    const val TEXT = "text"
    const val IMAGE = "image"
    const val VOICE = "voice"
    const val STICKER = "sticker"
    const val MUSIC = "music"
    /** A message its author deleted for everyone (content removed, placeholder shown). */
    const val DELETED = "deleted"
}

data class UserProfile(
    val uid: String,
    val name: String,
    /** Download URL in Firebase Storage ("" until the photo has been uploaded). */
    val photoUrl: String,
    /** Path of the photo saved on this phone, so the profile works instantly and offline. */
    val localPhoto: String = "",
    /** Friendly public ID others use to start a chat, e.g. "K7Q-4M2". */
    val shortId: String = "",
    val isAdmin: Boolean = false,
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
    val replyToId: String = "",
    val replyToName: String = "",
    val replyToText: String = "",
    /** uid -> emoji */
    val reactions: Map<String, String> = emptyMap(),
) {
    /** One-line description used for reply previews. */
    val preview: String
        get() = when (type) {
            MessageType.IMAGE -> "📷 Photo"
            MessageType.STICKER -> "Sticker"
            MessageType.VOICE -> "🎤 Voice message"
            MessageType.MUSIC -> "🎵 $text"
            MessageType.DELETED -> "Deleted message"
            else -> text
        }
}

/** A chat room (1:1 private room, or the shared lobby "main"). */
data class RoomInfo(
    val id: String,
    val members: List<String>,
    val theme: String = "wallpaper",
    val lastActivityMs: Long = 0L,
) {
    val isMain: Boolean get() = id == "main"
    fun otherUid(myUid: String): String? = members.firstOrNull { it != myUid }

    companion object {
        fun from(id: String, data: Map<String, Any?>): RoomInfo = RoomInfo(
            id = id,
            members = (data["members"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            theme = (data["theme"] as? String) ?: "wallpaper",
            lastActivityMs = (data["lastActivity"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L,
        )
    }
}

/** A device/user seen in a room, for the admin console. */
data class Presence(
    val uid: String,
    val name: String,
    val photoUrl: String,
    val shortId: String,
    val lastSeenMs: Long,
    val device: String,
)

/** Who a new message replies to. */
data class ReplyTarget(val id: String, val name: String, val text: String)

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
