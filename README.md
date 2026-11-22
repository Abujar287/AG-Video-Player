# AG Video Player (Android)

A lightweight, hardware-accelerated Android video player built with Kotlin, Jetpack Compose, Material 3, and AndroidX Media3 (`ExoPlayer` + `MediaSessionService`).

## Features
- **Folder & Library Browser**: Grid and List modes, live search, 5-field sorting (Name, Size, Date, Type, Plays), Favourites, Custom Playlists, and multi-select file actions (`Rename`, `Move`, `Delete`, `Share`, `Properties`).
- **Seamless File Moving**: Supports direct cross-folder file moves on Android 10–16 with explicit storage permission prompts (`MediaStore.createWriteRequest` and All Files Access) instead of failing on restricted `primary:` SAF root paths.
- **Fullscreen Media3 Player**: HW / SW decoder toggle, 200% `LoudnessEnhancer` audio boost, speed selector (`0.5x`–`2.0x`), aspect ratio cycling (`Fit`, `Stretch`, `Crop`), screen lock, rotation toggle, sleep timer, Picture-in-Picture (`PiP`), Background Audio Play, external `.srt` / `.vtt` / `.ass` subtitles, and Room playback resume history.
- **Configurable Gestures**: 1/2/3-finger gestures, single/double click actions, and horizontal swipe seek step (`Auto` / `10s` / `20s`).
- **In-App Updater**: Checks GitHub Releases (`Abujar287/AG-Video-Player`) with rate-limit-resilient fallback and downloads `AG Video Player.apk`.

## Build & Run
```bash
./gradlew assembleDebug
```
The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk` (and copied to `app/build/export/AG Video Player.apk`).
