# ROM modification status — P01Y / Z170CG WW

## Correct project scope

The project goal is to modify the stock ROM/system image for the ASUS ZenPad C 7.0 Z170CG / P01Y (WW), then provide a verifiable installation package for the user to flash. A standalone launcher APK is not the ROM deliverable.

The earlier work/car-launcher-lightweight-plan branch and PR #1 were only a launcher prototype and read-only inventory helper. They were superseded and closed so they cannot be mistaken for a modified ROM.

Active work branch: rom/p01y-system-modification.

## Verified starting point

The matching ASUS OTA release asset is UL-P01Y-WW-12.26.4.69-user_2.zip.

- SHA-256: a3cff1f6703cb68cbe189fc029ab0a94fd7a3ab85297cfdd271fe3e7ff16a6a2
- Model/SKU: P01Y / WW
- Android: 5.0.2, API 21, x86 userspace
- The OTA contains a full ext4 system block payload that was reconstructed and verified during analysis.
- The OTA does not contain boot.img or recovery.img.

## Active implementation sequence

1. Reconstruct the stock ext4 system partition from the OTA with strict SHA-256, ZIP CRC, transfer-list, block-count, and ext4 checks.
2. Mount the reconstructed image read-only and generate an APK/package inventory, including app path, package name, label, version, SDK, APK size, and privileged/system location.
3. Audit package dependencies and identify only safe optional applications. Do not remove Google, telephony, Bluetooth, audio, input, storage, location, ASUS battery/touch/panel services, framework, or boot-critical components based on name alone.
4. Add a reviewed, explicit removal manifest and build a modified ext4 system.img, preserving a copy of removed APK payloads and generating hashes and a change report.
5. Verify filesystem integrity and compare the modified image against the exact stock image.
6. Separately verify bootloader access, partition size/name, write method, OTA signature enforcement, and a working restore route before labeling any package flashable.

## Non-negotiable safety constraints

- Never flash the Intel fwu_image.bin from the OTA as part of debloating.
- Never guess partition IDs, sizes, or fastboot syntax.
- Never claim a standalone APK is a ROM.
- Never publish an image as flashable unless its installation path and rollback have been validated for P01Y WW.
- Preserve source image and original release asset unchanged.
- Keep every modified file and every removed package in a machine-readable manifest.

## Current run

The first active workflow reconstructs the exact stock system image, inspects its APK manifests read-only, and uploads the inventory. It does not yet remove packages or publish a flash image. This is required for an evidence-based debloat pass rather than guessing package names.
