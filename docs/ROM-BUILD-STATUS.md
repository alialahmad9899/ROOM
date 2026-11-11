# ROM modification status — P01Y / Z170CG WW

## Correct project scope

The active deliverable is a modified system image for the ASUS ZenPad C 7.0 Z170CG / P01Y (WW), including the ALI Car System interface. A standalone launcher APK is not the ROM deliverable.

Project identity is **ALI Car System**. The previous Wesal-branded launcher prototype is superseded; its code is not used in the ALI system image.

Active implementation branch: `ali-car-system/android5-build` (based on the verified P01Y system-candidate branch).

## Verified starting point
- ASUS OTA: `UL-P01Y-WW-12.26.4.69-user_2.zip`
- SHA-256: `a3cff1f6703cb68cbe189fc029ab0a94fd7a3ab85297cfdd271fe3e7ff16a6a2`
- Device: P01Y / WW; Android 5.0.2 / API 21 / x86 userspace.
- Reconstructed stock `system` is ext4.
- OTA does not include `boot.img` or `recovery.img`.

## ALI system implementation
1. Arabic RTL car dashboard for 1024×600 landscape with ALI / AL-AHMAD / VAO identity and GPS speedometer.
2. YouTube Lite web wrapper using the official mobile website; legacy WebView compatibility remains a runtime limitation.
3. Integrated local music player using MediaStore, including indexed removable-storage audio; playback pauses on screen-off and uses no CPU wakelock.
4. Phone, Wi-Fi, Bluetooth, Maps, reader and device-settings shortcuts.
5. Boot animation and English TTS welcome greeting with short two-tone chime.
6. System app integration and BOOT_COMPLETED receiver; ASUS launcher is retained as fallback.
7. 34 explicit audited removals, with original app directories and modified config files preserved for rollback.
8. Read-only verification of protected packages, app removal, backup archive and boot animation.

## Power management
The app does not force-kill Android services or freeze kernel/system processes. It releases GPS updates and does not hold partial CPU wakelocks. Music pauses on screen-off, allowing the normal Linux suspend-to-RAM path to operate if the stock kernel and power HAL support it. Android 5 has no Doze mode. Actual suspend current and wake reliability require physical-device validation.

## Not included / not claimed
- No Android 8 port; this work intentionally uses the matching stock Android 5 base.
- No new kernel, boot image, recovery image, modem, Intel firmware, partition table or audio HAL.
- No guarantee of modern YouTube compatibility on the original WebView.
- No guarantee of zero power draw while screen is off.
- No flash-ready status until bootloader, verified-boot behavior, exact system partition mapping/capacity and tested restoration route are confirmed.
