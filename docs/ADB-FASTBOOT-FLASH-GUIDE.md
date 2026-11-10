# ALI P01Y — ADB + Fastboot flash instructions

## Target and image identity

This guide is only for ASUS ZenPad C 7.0 Z170CG / P01Y, SKU WW, running the matching ASUS Android 5.0.2 build 12.26.4.69.

The image is the system partition only:

- File: **system.img**
- Size: **2,052,186,112 bytes**
- SHA-256: **f40c224453a85f09a811cc365dd291db6def72d659d5d764f0dba759d35eea89**
- Target Fastboot partition: **system**
- Matching OTA block-device name: **/dev/block/platform/soc0/e0000000.noc/by-name/ImcPartID068**

The stock OTA updater script targets the same partition through ImcPartID068. A published P01Y Fastboot report for this model identifies SF_3GR, an ext4 system partition, and size 0x90000000 (2,415,919,104 bytes). The local script checks those values on the connected tablet instead of trusting the report blindly.

## Files to place together on Windows

1. **system.img**
2. **flash-ali-p01y.bat**
3. **flash-ali-p01y.ps1**

Install Android SDK Platform-Tools so both adb and fastboot are available in PATH. Enable USB debugging and authorize this computer on the tablet.

## Run

Double-click **flash-ali-p01y.bat**.

The utility performs these checks before writing anything:

1. Exact candidate image size, SHA-256 and ext4 signature.
2. One authorized ADB device.
3. Device ID P01Y or P01Y_2, Android 5.0.2 / API 21, ASUS WW 12.26.4.69 build identity.
4. The live /system mount source. It stops if it sees a device-mapper source (possible dm-verity) or cannot identify the expected ImcPartID068 partition.
5. Fastboot product SF_3GR, unlocked bootloader, ext4 system type and exact capacity 0x90000000.
6. An explicit typed confirmation before the write.

The tablet is moved from Android to its Fastboot/Droidboot interface using adb reboot fastboot. Fastboot then writes only system using the image hash pinned in the script.

## What this utility will not do

- It will not unlock the bootloader.
- It will not run fastboot -w, erase userdata, or format cache.
- It will not flash boot, recovery, modem, Intel firmware or bootloader.
- It will not try a guessed partition name if the expected one is not reported.
- It will not bypass a possible dm-verity mapping.

**Important:** The known P01Y unlock procedure warns that unlocking erases user data. If the script reports unlocked: NO, stop. Do not run unlock commands until the data is backed up and the user explicitly accepts the wipe.

If the script stops because /system is mounted from /dev/block/dm-*, or because the mount source cannot be identified, do not flash. The stock boot ramdisk's verity configuration must be audited first. The OTA supplied for this device does not contain boot.img or recovery.img, so this utility does not claim to disable verified boot.

## Important limitations

This is a raw ext4 system.img, not an ASUS-signed OTA package. Stock recovery's adb sideload is not the selected path because the modified file is not signed with ASUS's private release key. ADB is used to enter Fastboot; Fastboot performs the actual partition write.

A successful Fastboot write is not proof that Android boots. Physical first-boot testing, launcher selection, GPS, Maps, Bluetooth, SIM/calls, AUX, Play Store and screen-off suspend behavior remain to be tested on the tablet.

Do not disconnect USB while the write is running. If Fastboot reports a write failure, leave the tablet in Fastboot and retain the full console output for diagnosis.

## Evidence

- P01Y-specific Fastboot procedure and example values: https://blog.stigok.com/2017/03/06/unlock-bootloader-asus-zenpad-z170cg-p01y.html
- ASUS official firmware page states that V12.26.4.69 is for Z170CG WW 2M/5M SKU only: https://www.asus.com/uk/supportonly/asus%20zenpad%20c%207.0%20%28z170cg%29/helpdesk_bios/
- Project OTA analysis: docs/STOCK-OTA-ANALYSIS.md
