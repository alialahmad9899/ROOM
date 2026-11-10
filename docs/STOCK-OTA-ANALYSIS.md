# Stock OTA analysis — P01Y WW 12.26.4.69

Inspection date: 2026-11-10  
Release: [stock-firmware-v1](https://github.com/alialahmad9899/ROOM/releases/tag/stock-firmware-v1)  
Asset: `UL-P01Y-WW-12.26.4.69-user_2.zip`

## 1. Integrity

- Downloaded bytes: 1,121,160,179
- SHA-256: `a3cff1f6703cb68cbe189fc029ab0a94fd7a3ab85297cfdd271fe3e7ff16a6a2`
- The independent download hash exactly matches the user's supplied hash and the GitHub asset digest.
- ZIP CRC test: passed.

The original release asset was not modified.

## 2. Package classification

This file is an ASUS recovery/OTA update package, not a complete factory RAWDATA/AFT image.

The ZIP contains 13 files, including:

- `system.new.dat` — 2,327,289,856 bytes uncompressed inside the outer ZIP.
- `system.transfer.list` — block update instructions.
- `fwu_image.bin` — 28,187,136 bytes.
- `META-INF/com/google/android/updater-script`
- OTA metadata and signing files.

It contains no `boot.img`, `recovery.img`, or complete partition image set.

## 3. Exact Android build extracted from system image

The transfer-list data was reconstructed to a temporary ext4 system image. Its ext4 superblock magic was verified as `53ef`; `/build.prop` was read from that image using `debugfs`.

Verified values:

| Property | Value |
|---|---|
| Android release | **5.0.2** |
| SDK | 21 |
| Build ID | `LRX22G` |
| Build display ID | `LRX22G.WW_ZenPad-12.26.4.69-20170410` |
| Incremental build | `WW_ZenPad-12.26.4.69-20170410` |
| Build date | 2017-04-10 17:02:05 CST |
| Security patch | 2017-04-01 |
| Build type | `user` |
| Signing tag | `release-keys` |
| Manufacturer / brand | ASUS |
| Product / device / model | `WW_P01Y` / `P01Y` / `P01Y` |
| SKU | `WW` |
| Board platform | `sofia3g` |
| Primary ABI | `x86` |
| ABI list | `x86,armeabi-v7a,armeabi` |
| 64-bit ABI list | Empty |
| Fingerprint | `asus/WW_P01Y/P01Y:5.0.2/LRX22G/WW_ZenPad-12.26.4.69-20170410:user/release-keys` |

Important: although the Intel Atom processor is 64-bit capable and the kernel source has both 32-bit and 64-bit defconfigs, this stock Android userspace is **32-bit x86**. Do not select a 64-bit Android userspace target based only on the CPU's capability.

The extracted build properties are also included as `system-build.prop` in the workflow artifact.

## 4. Transfer-list reconstruction

- Transfer-list version: 1.
- Declared new data blocks: 568,186.
- Data bytes: 2,327,289,856; this exactly matches `system.new.dat`.
- Highest exclusive block endpoint: 577,621.
- Reconstructed system image size: 2,365,935,616 bytes.
- New data bytes consumed: 2,327,289,856.
- Ext4 superblock magic: `53ef`.
- Reconstruction commands: `erase` and `new`; no source-image patch/stash commands were required.

This reconstructed image is the **system partition only**. It is not a complete device firmware package.

## 5. Update script target checks

The updater script checks that:

- the target model property is `P01Y`;
- the ASUS SKU is `WW` (unless an explicit `allow_update()` override is true);
- the installed build is not newer than the package date.

It calls `block_image_update` for:

`/dev/block/platform/soc0/e0000000.noc/by-name/ImcPartID068`

It also writes `fwu_image.bin` to:

`/dev/block/platform/soc0/e0000000.noc/by-name/ImcPartID122`

and calls the Intel firmware updater for that image. These targets are documented from the package script; they are **not** a complete partition map.

## 6. What is still missing

To safely customize, rebuild, and recover this tablet, we still need a matching full factory RAWDATA/AFT package or equivalent verified device backups, especially:

- stock boot image (kernel + ramdisk);
- stock recovery image;
- droidboot/fastboot or bootloader-related image/configuration where applicable;
- full partition map and sizes;
- any remaining vendor firmware and device-specific configuration required for recovery.

The matching package name reported by third-party firmware archives is:

`WW_Z170CG_V12.26.4.69-user-201704101658-rawdata.zip`

Candidate listings:

- [HalabTech file listing](https://support.halabtech.com/index.php?a=downloads&b=file&id=350604) — lists the matching RAWDATA archive at 2.00 GB.
- [RomDevelopers file listing](https://romdevelopers.com/index.php?a=downloads&b=file&id=43686) — lists the same archive at 2.20 GB and displays a purchase-download prompt.

These are third-party listings, not ASUS-hosted links, and neither download was verified. Confirm exact P01Y / Z170CG / WW identity and verify the archive checksum before use. Do not substitute a Z170C (P01Z) or Z170MG (MediaTek) RAW package.

## 7. Safety

- No image was flashed or executed.
- No partition was written on the physical tablet.
- No boot or recovery image has been built.
- The temporary reconstructed system image was used only for read-only analysis.
- Do not flash `fwu_image.bin` or write to either partition ID based solely on this OTA script.
