# Spotify Pojav — Minecraft music HUD

Experimental Fabric client mod for Minecraft Java 1.21.11 with an Android companion intended for Zalith/Pojav.

## Features
- Spotify track, artist, playback state and progress bar in a compact purple/cyan HUD.
- Current and upcoming lyric line synchronized to playback position.
- Import a local timed lyrics file (`.lrc`) using the companion app's file picker.
- Toggle the HUD with Right Shift (default).
- Local bridge at `http://127.0.0.1:8765/now`, polled once per second.

## Setup
1. Build/install the Android companion APK from the GitHub Actions artifact `spotify-pojav-android`.
2. In Spotify settings, enable **Device Broadcast Status**.
3. Start a track in Spotify.
4. Open Spotify Pojav Bridge and keep it open while playing.
5. Tap **Cargar letras (.lrc)** and choose the matching timed lyrics file.
6. Launch Minecraft with the Fabric mod installed.

## LRC example
```text
[00:05.20]First timed line
[00:09.80]Second timed line
[00:14.00]Third timed line
```
The app parses timestamps and selects the current and next lines using Spotify's playback position. It does not scrape or redistribute lyrics.

## Build
- Mod: Java 21, `gradle build`; JAR in `build/libs/`.
- Companion: `gradle -p android assembleDebug`; APK in `android/app/build/outputs/apk/debug/`.

## Compatibility
The companion listens for Spotify's optional Android media broadcasts. Spotify must expose them; the companion cannot enable that setting. Keep the companion open because the local bridge currently runs only while its Activity is alive.

The project is experimental and still needs a successful CI build and real-device testing on Zalith. This version displays lyrics in the 2D HUD; floating 3D lyrics are not included yet.
