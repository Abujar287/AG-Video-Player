# AG Video Player (React)

A lightweight, responsive React + TypeScript video player rewritten from the original Android Jetpack Compose application.

## Features
- **Folder & Library Browser**: Grid and List modes, live search, 5-field sorting (Name, Size, Date, Type, Plays), Favourites, Custom Playlists, and multi-select file actions (`Rename`, `Move`, `Delete`, `Share`, `Properties`).
- **Local Video Import & Persistence**: Includes bundled preview clips (`Downloads` and `Movies`) plus local file picker and drag-and-drop import persisted in IndexedDB, with playback resume history and preferences saved in `localStorage`.
- **Fullscreen Video Player**: HTML5 video playback with Web Audio API 200% audio boost, HW/SW decoder preference toggle, speed selector (`0.5x`–`2.0x`), aspect ratio cycling (`Fit`, `Stretch`, `Crop`), screen lock, orientation/fullscreen toggle, sleep timer, Picture-in-Picture (`PiP`), Background Audio Play mini-bar, and client-side external `.srt` / `.vtt` / `.ass` subtitle loading.
- **Configurable Gestures & Overlay**: Single/double click actions, horizontal swipe seek (`Auto` / `10s` / `20s`), left brightness / right volume swipe HUDs, and customizable player overlay button visibility.
- **In-App Updater**: Checks GitHub Releases (`Abujar287/AG-Video-Player`) for newer releases.

## Development & Build
```bash
npm install
npm run dev
npm run build
```
