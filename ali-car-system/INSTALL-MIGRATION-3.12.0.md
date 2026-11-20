# ALI CAR 3.12.0 — one-time migration from 3.11.x

The 3.11.x CI APKs were signed by temporary debug certificates. The 3.12.x test line uses a stable test-only certificate, so Android will reject an in-place update from 3.11.x.

## Preserve settings first
1. In the installed 3.11.x app, open **Settings**.
2. Choose **Create backup** and save the JSON file to Downloads or a computer.
3. Keep your audio files in shared storage under **Music/ALI CAR**. Do not delete that folder.

## Install 3.12.0
1. Uninstall the old ALI CAR app once.
2. Install the 3.12.0 APK.
3. Open Settings → **Restore backup** and select your saved JSON file.
4. Open Navigation and allow the embedded Syria map, POI data and BRouter files to be verified/copied. Keep over 200 MB free in internal storage.

The JSON backup restores supported app settings, saved destinations, playlists/favorites and dashboard preferences. It does not include the large map data or audio files. Maps are contained in the APK; music remains in shared storage.

**Important:** the keystore in this repository is deliberately a public test key, not a production secret. Do not use this build for Play Store or production distribution.
