# P01Y WW — Wesal car system candidate

This build candidate is based only on the verified ASUS P01Y WW Android 5.0.2 system image.

## What this build changes
- Adds Wesal Car Launcher as a system application under /system/app/WesalCarLauncher.
- Removes the exact packages listed in ROM-MODIFICATIONS.json after checking their stock APK manifests.
- Keeps Google Play services, Google Services Framework, Maps, TTS, location, Google Play Store, WebView providers, phone/SIM, Bluetooth, ASUS Music, settings, SystemUI, ASUS sensor/input/power services, file manager, SuperNote, Kindle, Zinio, Gallery, Contacts, Messaging, and the original ASUS launcher fallback.
- Cleans only the matching package entries from the active WW hidden-app list and removes the Gmail data-migration entry because Gmail is removed.
- Saves the complete removed application directories and original edited config files for rollback.

## What this build does not change
- boot.img, recovery.img, kernel, modem, Intel fwu_image.bin, partition table, or bootloader.
- Google Play services / Maps foundation.
- The device's user-data partition.

## Important — do not flash until device checks are complete
This is a **system-partition candidate**, not an ASUS-signed OTA and not a complete factory firmware package. The original OTA did not include boot.img or recovery.img. Before flashing, verify on the actual tablet:
1. Exact product is P01Y / SF_3GR and SKU WW.
2. Bootloader is already unlocked or a separately approved unlock process is completed. Unlocking can wipe user data.
3. Actual `system` partition size is at least the byte size of the resulting image.
4. The stock boot image does not enforce dm-verity against the modified system, or a compatible verified boot plan has been established.
5. A known-good return-to-stock route is available.

No command in this build flashes a device. The image must not be treated as flash-ready until those checks pass.
