# P01Y WW — ALI Car System candidate

This build is based on the verified ASUS P01Y WW Android 5.0.2 / API 21 / 32-bit x86 stock system.

## Integrated ALI features
- Installs `com.ali.carsystem` under `/system/app/ALICarSystem/ALICarSystem.apk` as a system application and HOME/launcher candidate.
- Arabic RTL landscape dashboard for the P01Y 1024×600 display, ALI / AL-AHMAD / VAO branding, GPS speedometer, navigation, YouTube Lite web wrapper, local music, phone, Wi-Fi, Bluetooth, reader and settings shortcuts.
- Adds a 1024×600 ALI boot animation at `/system/media/bootanimation.zip`.
- A boot receiver opens the ALI dashboard and requests an English TTS greeting: “Welcome Ali. Developed by Ali Ahmad.” A short two-tone chime plays first. The actual English voice availability depends on the installed TTS engine and language data.
- Local music player reads Android MediaStore audio items, including items indexed from removable storage. Music can continue while another app is foregrounded, but pauses when the screen turns off; no partial wakelock is held.
- YouTube Lite loads the official mobile YouTube site in Android WebView. Current site compatibility on the factory Android 5 WebView is not guaranteed.

## Preserved device functions
The debloat manifest retains Google Maps/location foundations, Google Play services/GSF/Play Store/TTS/WebView, phone and telephony providers, Intel telephony app, Bluetooth, ASUS Music and hardware services, settings/SystemUI, sensors/input/power services, file manager, reading apps, Gallery, Contacts and Messaging. The stock kernel, audio HAL, modem, SIM drivers and device configuration are not replaced.

The 34 explicitly audited removal entries remain limited to selected promotional, social, shopping, duplicate media, and unused ASUS applications. The full original removed app directories are archived for rollback.

## Screen-off and battery behavior
The ALI app does not run a permanent service or hold CPU wake locks. GPS updates are released when the dashboard loses foreground; local music pauses on screen-off. Android/Linux is then allowed to enter the kernel's normal suspend-to-RAM path, if supported by the device kernel and power HAL. We do not forcibly freeze system processes or disable telephony, Wi-Fi, Bluetooth, or wake-up input drivers. This preserves the Power button wake path and avoids breaking essential services.

Android 5 does not have Android 6+ Doze. Deep suspend is kernel/device dependent and must be validated on the physical tablet; no software-only build can promise zero background current. Wi-Fi/Bluetooth and modem standby power may remain depending on stock policy.

## What remains unchanged
- boot.img, recovery.img, kernel, modem, Intel firmware image, partition table and bootloader.
- User data partition.
- ASUS launcher fallback; first boot may ask the user to select ALI as HOME. The boot receiver also opens ALI after BOOT_COMPLETED.

## Known limitations
- The official YouTube mobile site may reject or fail on the legacy Android 5 WebView. The wrapper does not bypass YouTube requirements or ads.
- English TTS greeting requires an installed English voice in the retained TTS engine; otherwise the dashboard still starts without the spoken greeting.
- Android dual-SIM hardware status is only summarized by the current public Android API; both SIM slots and Intel telephony packages are retained, but call/SIM operation needs on-device validation.
- AUX output remains governed by the stock audio HAL and analog routing; the ROM does not add unsupported hardware drivers.
- Android 8 is deferred by design; this build targets the known-matching Android 5 firmware and device drivers.

## Flash safety
This is a modified system-partition candidate, not a complete factory image or an ASUS-signed OTA. It does not contain a replacement boot or recovery image. Do not flash until exact system partition mapping/capacity, bootloader write support, verified-boot behavior and a reliable stock restore route are established.