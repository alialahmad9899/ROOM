# ASUS ZenPad C 7.0 (Z170CG / P01Y) — Car Tablet ROM Project

This repository tracks the verified device baseline and the work needed to create a safer, car-oriented Android setup.

## Current status

- Original ASUS kernel-source archive is stored as a GitHub Release asset, not expanded into Git history.
- The uploaded archive passed SHA-256 and ZIP CRC verification.
- Kernel and board-source inspection is complete for the available source package.
- The complete stock firmware image has **not** yet been added or modified.
- No custom ROM has been built and no flashing has been attempted.

## Confirmed device baseline

See [docs/DEVICE-BASELINE.md](docs/DEVICE-BASELINE.md) for the evidence-backed inventory and the next required input.

## Original kernel source release

[ASUS Z170CG Kernel Source 12.26.4.69](https://github.com/alialahmad9899/ROOM/releases/tag/kernel-source-v1)

## Safety rule

Do not flash a boot, recovery, system, or firmware image until the exact device/SKU, partition layout, boot image format, bootloader state, and a verified recovery path have been confirmed.
