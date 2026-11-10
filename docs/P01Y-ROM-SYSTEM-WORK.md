# P01Y ROM system-image work

This branch is for modifying the Android system image itself, not just building a standalone launcher.

## Verified input
- Device: ASUS ZenPad C 7.0 Z170CG / P01Y / WW
- OTA: \`UL-P01Y-WW-12.26.4.69-user_2.zip\`
- SHA-256: \`a3cff1f6703cb68cbe189fc029ab0a94fd7a3ab85297cfdd271fe3e7ff16a6a2\`
- Stock Android: 5.0.2 / API 21 / x86

## Current operation
The workflow downloads the original OTA again, verifies its exact size and SHA-256, reconstructs the ext4 system partition from the transfer list, and inspects APK manifests from the system and priv-app directories. It uploads only a CSV/report artifact; it does not publish or flash any partition image.

## Why this is the first ROM step
We must identify every actual package in this exact factory image before deciding what can be removed. The intended system profile is:
- Keep Android core, touch/display, audio/media, storage, settings, package installer, power/battery/thermal, USB, GPS/location, Wi-Fi and Bluetooth.
- Keep the phone/SIM/radio stack if mobile network is part of the intended use.
- Keep only the Google components needed by the selected navigation solution; test compatibility before removing Google packages.
- Remove or disable confirmed OEM demos, duplicate apps, promotions, unused sync/cloud apps and other optional applications only after dependency review.

## Flashing safety
The current OTA is a recovery update package signed by ASUS. Editing \`system.new.dat\` invalidates its original signature. This branch does not claim that a modified OTA can be installed through stock recovery.

Before delivering a flashable package, the exact P01Y flashing route must be established (bootloader lock/unlock state, supported fastboot commands or custom recovery, system partition size/target, and recovery path). The project will not invent a partition name or publish a package that could overwrite the wrong partition.

## No local user steps
Inspection and builds run in GitHub Actions. The device owner is not asked to open VS Code, extract source trees, or execute the inventory script locally.
