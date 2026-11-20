# ALI CAR 3.12.0 — API 21 / x86

## Stability and map rendering
- MapsForge source discovery and file parsing now run off the UI thread; only provider attachment and camera updates run on the main thread.
- Navigation opens map-first with collapsible search/tools and route-guidance panels.
- Offline rendering is validated with a non-empty tile-detail check across Damascus, Homs, Aleppo, Latakia and Daraa (at least four of five city tiles must contain rendered detail).
- The offline package is SHA-256 verified in a background task before being trusted. Verification is cached for the current process using manifest/file metadata and repeated when metadata changes.

## GPS correctness
- Navigation live fixes are limited to 30 seconds and 150 m accuracy.
- Route-start fixes are limited to 45 seconds and 250 m accuracy.
- Fused/network estimates outside those limits are rejected.
- Vehicle speed, trip distance and odometer accept GNSS satellite-provider fixes only; network estimates cannot inflate speed or distance.

## Dashboard and operations
- Main clock updates stop while the dashboard is in the background.
- CI captures the dashboard, music, settings, phone and navigation screens on a configured 1024×600 / 170 dpi Android 5.0.2 x86 emulator.
- SHA256SUMS uses release-root-relative filenames and BUILD-INFO contains correctly formatted byte counts.

## Stable test signing
- All 3.12.x CI test APKs use one fixed, public **test-only** certificate.
- Certificate SHA-256: `D87A5C149B9CBE6507672CDBA137281F78D35941C527B88D925B0A0D4B1842F4`.
- This certificate is public and is NOT a production/private distribution key.

## One-time installation migration
The historic 3.11.x CI APKs were signed with different ephemeral debug certificates. Android cannot install this 3.12.0 build as an in-place update over those builds.

Before removing 3.11.x:
1. Open ALI CAR → Settings → Create backup and save the JSON file.
2. Keep songs in the shared `Music/ALI CAR` folder; they are not deleted by uninstalling the app.
3. Uninstall the old ALI CAR app once, then install 3.12.0.
4. Open Settings → Restore backup and select the saved JSON.
5. Allow the embedded offline Syria map package to be verified/copied on first navigation launch.

The settings backup preserves supported app preferences, playlists/favorites, saved navigation data and dashboard preferences. It does not include the 180 MB map package or the audio files themselves; the maps are embedded in the new APK and music remains in shared storage.

## Validation boundary
This is a debug-signed test release for Android 5.0 / API 21 / x86 32-bit. GNSS satellite reception, cellular network location, Bluetooth A2DP pairing and long real-car journeys still require validation on the physical ASUS P01Y device.
