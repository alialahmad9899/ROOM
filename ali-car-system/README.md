# ALI CAR — Clean Native Android 5 Launcher

This branch replaces the previous launcher implementation with a fresh, low-overhead native Android application.

## Target

- Device profile: ASUS ZenPad P01Y / 1024×600 / Intel x86 / 1 GB RAM
- Minimum Android: 5.0 / API 21
- Target SDK: 22
- Application ID: `com.ali.carsystem`
- UI: native Android Views and AndroidX RecyclerView; **no WebView source or runtime**
- Map: OSMDroid, loaded only inside the navigation activity
- Video: `MediaPlayer + SurfaceView`; progressive MP4 only at 360p/480p when the source backend offers it
- Background: one small WebP file decoded with `RGB_565`

## Main dashboard

Top row: ALI CAR branding, Wi-Fi/Bluetooth states, digital clock and rounded settings shortcut.
Center-left: GPS speed dial and previous/play/next media shortcuts.
Center-right: large digital clock, custom analog clock and English date.
Bottom dock contains exactly six actions: YouTube Lite, Navigation, Music, Bluetooth, Wi-Fi and Brightness. Credit text is displayed under the dock.

## Video search and playback

Search tries Piped API instances first, then the official Invidious instance registry and a small documented fallback list. Public community instances are independent services and can become unavailable, rate-limit, or change API behaviour. Piped’s documentation explicitly recommends dynamically parsing its public instance list. Playback accepts only progressive `video/mp4` 360p/480p streams; it does not silently switch to DASH/HLS, higher resolutions, or a WebView. If no compatible progressive stream is offered, the app reports that instead of pretending playback is supported.

The Android platform decoder is selected by MediaPlayer/MediaCodec where available. Smooth playback is not guaranteed by software changes alone: the selected stream, server proxy, codec support, WebView is not involved, thermal state and available RAM still matter.

## Device Owner and Kiosk

The admin screen can request ordinary Device Admin, display an explicit safe optional-package allowlist, and perform app hiding or kiosk allowlisting **only when Android reports this package is the Device Owner**. It does not grant itself Device Owner. Device Owner provisioning normally happens during initial device setup, commonly using ADB after a factory reset:

```sh
adb shell dpm set-device-owner com.ali.carsystem/.CarAdminReceiver
```

This is not an instruction to run on an already-configured device without checking its owner/accounts state. Hiding packages can remove features; do not hide system UI, telephony, Bluetooth, location, input, package installer, settings, Google Services Framework, or Play Services.

## RAM and validation

The app avoids WebView, keeps map downloads out of the home screen, uses RGB_565 for the wallpaper, limits the OSMDroid tile cache, and releases MediaPlayer/MapView resources on exit. The requested 60 MB ceiling is a **measurement target, not a guarantee**. It must be measured on the actual tablet using Android memory tools while video, map and music screens are exercised. Public API/network reliability and the device decoder cannot be proven by a successful compile.

CI performs static project-contract checks, compiles the APK, verifies package metadata and APK signatures. This is a debug-signed installable build, not a production release key. No emulator/device runtime instrumentation or physical-device playback test is implied by CI success.

## Network and attribution

- Piped API docs: https://docs.piped.video/docs/api-documentation/
- Invidious API and public instance list: https://docs.invidious.io/api/ and https://docs.invidious.io/instances/
- Map tiles: © OpenStreetMap contributors
- Geocoding: Nominatim
- Driving route: OSRM public demo service

Public endpoints are best-effort services, not an uptime guarantee. Respect their published usage policies and avoid automated high-frequency requests.
