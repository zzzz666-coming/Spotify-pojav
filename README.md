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
