# Spotify Pojav — Minecraft music HUD

A client-side Fabric mod prototype for Minecraft Java 1.21.11, designed for Android launchers such as Zalith/Pojav.

## Current status

This first commit adds the in-game HUD and a local bridge client. It does NOT yet read Spotify directly from Android. A separate Android companion/bridge is required because a Minecraft Java mod cannot automatically access Android media-session permissions.

## HUD

- Toggle with Right Shift by default.
- Displays track, artist, playback state and optional lyric line.
- Compact translucent purple/cyan panel.
- Reads JSON from http://127.0.0.1:8765/now every 2 seconds.
- If no bridge is running, it shows a disconnected state.

Expected JSON: {"title":"Track title","artist":"Artist name","playing":true,"positionMs":42000,"durationMs":210000,"lyric":"Current lyric line"}

## Build

Requires Java 21 and Gradle. Run gradle build. The mod JAR is generated in build/libs/.

## Android / Spotify notes

Spotify documents an optional Android Device Broadcast Status feature that can broadcast track metadata and playback position. The user must enable it in Spotify settings. A companion Android app will be needed to receive that broadcast and expose the local JSON endpoint. Until that companion is implemented and tested, Spotify integration is experimental and not yet available.

See Spotify's Android media notifications documentation: https://developer.spotify.com/documentation/android/tutorials/android-media-notifications

## Android companion (prototype)

The `android/` folder contains a companion app that listens for Spotify's documented Android media broadcasts while the app is open and serves the latest metadata at `127.0.0.1:8765/now`. On the phone, enable Spotify's **Device Broadcast Status** setting and keep the companion open. This receiver is best-effort: Android/Spotify may defer or omit broadcasts, and it has not yet been validated on a Galaxy A34 / Zalith session.

Build the companion with `gradle -p android assembleDebug`. The APK is output to `android/app/build/outputs/apk/debug/`.

Lyrics and in-world 3D lyric rendering are not implemented in this prototype yet. The HUD currently supports one lyric string from the bridge, but no lyrics provider is bundled.
