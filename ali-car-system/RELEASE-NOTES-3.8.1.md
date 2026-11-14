# ALI CAR 3.8.1 — Repair and validation notes

Target: ASUS ZenPad C 7.0 / P01Y, Intel SoFIA 3GR, 32-bit x86, Android 5.0 / API 21.

## Changes
- Align Gradle version identity with the packaged APK: versionCode 381, versionName 3.8.1, with no Debug-only suffix.
- Verify the actual APK versionName during CI before publishing.
- Remove the previous 30-track historical archive from the build pipeline and delete its catalogue.
- On upgrade, remove the obsolete archive and its MediaStore rows from shared and app-private legacy locations.
- Restrict the visible music library to the 25 named Syrian/Lebanese artists only. Normalize Arabic diacritics/alef variants and common Latin spellings in artist/title metadata.
- Continue to scan and import user-owned local audio; each track is limited to 4 MiB and no third-party commercial recordings are bundled.
- Add unit coverage for artist aliases and allowlist behavior.
- Add an API 21 instrumentation test for local MediaStore indexing and MediaPlayer play/pause/resume.
- Add a dashboard phone hub with contact-picker-to-dialer flow and Bluetooth settings/status; calls are placed through the system dialer and are never initiated silently.
- Expand offline BRouter integration coverage to Homs–Damascus, Damascus–Daraa, Homs–Tartus and Homs–Aleppo.
- Expand route progress tests for passed maneuvers, temporary GPS loss/recovery and invalid fixes.
- Upgrade GitHub Actions to maintained versions and promote the previously verified offline map package into a durable GitHub Release asset on first 3.8.1 build.

## Music content policy
The APK intentionally contains zero preloaded MP3/OGG recordings. The player only displays audio metadata matching the 25 named artists from the user's list. Commercial tracks must be supplied by the user or licensed for redistribution before they can be packaged offline. File size <=4 MB does not grant redistribution rights.

## Validation boundaries
CI tests run on an Android 5.0.2 x86 emulator. They cannot validate the physical tablet's GPS antenna, Bluetooth radio, speakers, vehicle charging behavior, thermal behavior, or long-duration playback. Those remain required device acceptance tests.
