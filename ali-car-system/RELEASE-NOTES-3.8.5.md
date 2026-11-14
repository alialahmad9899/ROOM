# ALI CAR 3.8.5 — Offline navigation, music and cockpit refinements

- Persistent MapsForge source reuse and a bounded 96 MiB disk tile cache (64 MiB trim threshold); the map stays vector-based and offline.
- Native-resolution map rendering, persisted center/zoom, and restored selected route.
- Saved and recent destinations, parking-position save/return, and automatic/manual map night tint.
- Dashboard navigation HUD anchored immediately below the music strip instead of the middle of the screen.
- Live Wi-Fi, Bluetooth and GPS indicators; smart dashboard night mode (20:00–06:00 by default).
- Music search, title/artist/recent sorting, duplicate review, SHA-256 duplicate prevention on imports, playlist removal and explicit-confirmation permanent deletion.
- Deletion cleans favorites, custom/category playlist exceptions, recent history and current playback queue.
- Private completed-trip history, newest-first and capped at 30 journeys.
- JSON backup/restore for app, music, navigation and map preferences. It excludes actual media files and the large offline map package; audio is not auto-started after restore.
- API 21 x86 emulator regression coverage for HUD positioning, new music controls, delete lifecycle, backup/restore and trip history.

Build target: Android 5.0 / API 21, x86 32-bit, versionCode 385.
