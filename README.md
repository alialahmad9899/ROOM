# ASUS ZenPad C 7.0 (Z170CG / P01Y) — ALI Car System ROM

The repository builds a device-specific, lightweight Arabic car interface and modified Android 5.0.2 system candidate for the ASUS ZenPad C 7.0 P01Y / WW.

## Active work
- Project identity: **ALI Car System** (not Wesal).
- Android base: stock ASUS WW 12.26.4.69, Android 5.0.2 / API 21 / x86.
- ALI dashboard: Arabic RTL, landscape 1024×600, GPS speedometer, navigation, YouTube Lite web wrapper, local music, phone, Wi-Fi, Bluetooth, reader and settings.
- Boot experience: ALI / AL-AHMAD / VAO boot animation and English TTS greeting “Welcome Ali. Developed by Ali Ahmad.” with a short two-tone chime.
- Power: no permanent launcher service or CPU wakelock; GPS stops when UI leaves foreground; music pauses on screen-off so Linux suspend can engage. No forced process freeze.
- Debloat: 34 explicitly audited applications removed with original app directories archived; essential connectivity, telephony, Google, ASUS hardware and audio services remain protected.

## Build and evidence
See [docs/DEVICE-BASELINE.md](docs/DEVICE-BASELINE.md) and [docs/P01Y-WW-ROM-CANDIDATE.md](docs/P01Y-WW-ROM-CANDIDATE.md). The build workflow reconstructs the exact stock OTA, builds ALI, generates a matching boot animation, embeds both into the system image, applies the removal manifest, shrinks/checks ext4 and performs read-only content verification.

## Important limitations
- Android 8 is deferred; this release targets the known-good Android 5 stock hardware platform.
- Current YouTube website compatibility on the legacy Android 5 WebView is not guaranteed.
- Actual suspend-to-RAM behavior, English TTS voice availability, dual-SIM and AUX must be validated on the physical tablet.
- This is a system image candidate, not a complete flashable factory firmware package. Do not flash until partition mapping/capacity, bootloader and recovery path are verified.
