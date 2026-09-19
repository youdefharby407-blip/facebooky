# FaceBooky

Simple single-room chat for Android (Kotlin + Jetpack Compose) on Firebase:
Anonymous Auth, Firestore (real-time chat + signaling + shared music state), Cloud Storage (media),
WebRTC voice/video calls, voice messages, images, stickers, and synchronized "listen together" music.

## 1. Before the first build
1. Copy your `google-services.json` to `app/google-services.json`
   (Firebase Console → Project settings → Android app with package `com.yousef.facebooky`).
2. Firebase Console:
   - **Authentication → Sign-in method → Anonymous**: enabled (already done).
   - No Cloud Storage bucket is needed: all media is stored in Firestore (`blobs/`), so the free Spark plan is enough.
     Free quota: 1 GiB stored, 50k reads + 20k writes per day. Songs are limited to 15 MB.
   - Deploy the rules (below). With the old `if false` rules the app opens but shows "Chat is locked".

## 2. Deploy security rules
Either paste the files into the console (Firestore → Rules, Storage → Rules) or use the CLI:
```
npm i -g firebase-tools
firebase login
firebase use --add            # pick your project
firebase deploy --only firestore:rules
```
File: `firebase/firestore.rules` (wired in `firebase.json`, tested by the "Test security rules" workflow).
No composite indexes are needed.

## 3. Build
Android Studio (Ladybug or newer): *Open* this folder → wait for Gradle sync → Run ▶.

Command line (JDK 17 + Android SDK 35):
```
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions: push to a **private** repo, add secret `GOOGLE_SERVICES_JSON` (file content),
then Actions → *Build Debug APK* → download artifact `FaceBooky-debug-apk`.

## 4. TURN (optional, for calls across strict/mobile networks)
STUN (Google) is configured. If calls say "Connection failed. A TURN server may be needed",
fill `TURN_URL`, `TURN_USERNAME`, `TURN_PASSWORD` in
`app/src/main/java/com/yousef/facebooky/call/IceConfig.kt` (e.g. Metered, Twilio, or your own coturn).

## 5. Architecture
```
data/      Firebase repositories (Auth, User, Chat, Music, Storage) + models + paths
audio/     VoiceRecorder, VoiceMessagePlayer
music/     MusicController (shared playback sync, local-only mute)
call/      WebRtcClient, CallSignaling (Firestore), CallManager, IceConfig
ui/        ChatViewModel, Permissions, theme, chat/, sheets/, call/
util/      Network status, image processing, media helpers
```
Firestore: `users/{uid}`, `rooms/main/messages/{id}`, `rooms/main/music/{id}`,
`rooms/main/state/player`, `calls/{id}`, `calls/{id}/candidates/{id}`.
Media: `blobs/{id}` + `blobs/{id}/chunks/{i}` (<= 900 KB each), referenced as `blob:{id}` and cached on each phone.

## 6. Manual test checklist
Two phones: open app (no login) → read chat → send text (profile sheet appears once) →
photo, sticker, voice (hold/cancel/send) → Music: play default song, drag/tap seek bar on phone A,
phone B follows; mute on B, A keeps playing → voice call, video call (accept/decline/mute/speaker/switch/camera off/end) →
airplane mode (shows Offline, messages queue) → kill & reopen app → uninstall/reinstall: history still there.
