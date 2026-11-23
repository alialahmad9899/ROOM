# ALI CAR 3.11.2 — Cockpit functional repair

Based on the published 3.11.1 release for ASUS Z170CG / P01Y (Intel SoFIA 3GR, x86 32-bit, Android 5 / API 21).

## Fixes
- Dashboard dock actions are dispatched from the custom icon, its caption and the parent tile.
- The YouTube dock shortcut and Apps dialog return to the in-app YouTube search/player route: `YouTubeSearchActivity` → `VideoApi` → `VideoPlayerActivity` / ExoPlayer. The shortcut no longer redirects to Chrome or the YouTube mobile website.
- Restored music volume down, volume slider and volume up controls in the dashboard strip.
- Enlarged the dashboard typography and made the analog clock and speedometer use the same responsive dial dimensions.
- Local music pauses on active Bluetooth A2DP disconnection and resumes after the A2DP profile reconnects, only when playback was active before disconnection. Manual playback actions cancel the pending automatic resume.
- Kept the existing manual/automatic sleep behavior and expanded the regression test for brightness restoration and matching cockpit dials.

## Preserved
- Navigation and swipe/page behavior.
- Existing local music library, playlists and playback engine.
- Existing offline Syria map, POI database and BRouter routing data.
- Existing GPS filtering and distance/odometer rules. The GPS sampling interval is not increased; a small speed display delay is expected with one-second location requests.

## Verification
- CI runs unit tests, API 21 x86 instrumentation tests, a fresh build, APK package/signature/offline-payload checks, dashboard/navigation emulator checks and GPS-injected navigation diagnostics.
- Emulator results do not replace physical ASUS P01Y checks for Bluetooth A2DP, vehicle audio routing, real GNSS latency, or device-specific graphics behavior.
- APK is a debug-signed testing build, not a production-key release.
