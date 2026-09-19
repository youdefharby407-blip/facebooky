package com.yousef.facebooky.call

import org.webrtc.PeerConnection

/**
 * ICE servers. STUN works for most home / Wi-Fi networks.
 * Many mobile carriers use symmetric NAT: if calls connect on Wi-Fi but not on 4G/5G,
 * fill in a TURN server below (e.g. Cloudflare Calls TURN, Twilio, Metered, or your own coturn).
 */
object IceConfig {
    // TURN (optional) — example: "turn:turn.example.com:3478?transport=udp"
    private const val TURN_URL = ""
    private const val TURN_USERNAME = ""
    private const val TURN_PASSWORD = ""

    fun iceServers(): List<PeerConnection.IceServer> = buildList {
        add(
            PeerConnection.IceServer.builder(
                listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")
            ).createIceServer()
        )
        if (TURN_URL.isNotBlank()) {
            add(
                PeerConnection.IceServer.builder(TURN_URL)
                    .setUsername(TURN_USERNAME)
                    .setPassword(TURN_PASSWORD)
                    .createIceServer()
            )
        }
    }
}
