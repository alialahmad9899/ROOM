# ALI CAR 3.11.1 — Android 5 / x86

## Offline map rendering

- Centers the initial map on Damascus, inside the bundled Syria coverage.
- Rejects saved camera positions outside the installed vector map coverage.
- Prevents follow-car from moving the offline Syria map outside its map data extent; shows a clear message when GPS is outside the available coverage.
- Generates and verifies a real MapsForge tile around Damascus before declaring map display ready.
- Adds instrumentation assertions for the actual MapsForge provider, map bounds, initial camera and generated vector tile.

## Other repairs retained from 3.11.0

- GPS freshness, accuracy, coordinate and ordering validation, plus stale-speed timeout.
- SHA-256 manifest and storage preflight for offline map installation.
- Correct BRouter turn mappings and honest single-route UI.
- Vector icons for settings, sleep, previous and next media actions.
- Transient audio-focus auto-resume only when still desired.
- Expanded diagnostics and hidden-app checkbox state restoration.

Minimum Android 5.0 / API 21, x86 32-bit. Debug-signed test build. Physical ASUS P01Y tests remain required.
