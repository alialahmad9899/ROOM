# Device baseline — ASUS Z170CG / P01Y

Inspection date: 2026-11-10  
Repository branch inspected: `main`  
Stock firmware identifier supplied for this device: `UL-P01Y-WW-12.26.4.69-user`

## 1. Device identity and platform

The source package is explicitly named for ASUS Z170CG / P01Y and identifies the Intel SoFIA 3GR platform. The available kernel tree is x86-oriented and includes device-specific ASUS board support.

Important: this is not an ARM/MediaTek device tree. Any proposed ROM or kernel must match the tablet's Intel x86/SoFIA platform and its exact board revision.

## 2. Original source archive integrity

Release asset: `Z170_V12_26_4_69_Kernal.zip`  
Size: 129,001,094 bytes  
SHA-256: `1005cb54e93c9ffd49146c7d0add0054db633eb980b5cec9b4cd195a888b8c0a`  
ZIP CRC test: passed

The outer ZIP contains one nested source archive:

`l-sofia_3gr_v2-r4_V12.26.4.69-201704101658-foss.tar.gz`

The nested archive was readable and contains 51,256 archive members, including 48,123 regular files and about 556.5 MB of uncompressed regular-file content.

## 3. Kernel findings

The kernel source directory is named `linux-3.10`, but its root `Makefile` declares:

- `VERSION = 3`
- `PATCHLEVEL = 14`
- `SUBLEVEL = 0`

Therefore the actual declared kernel version is **Linux 3.14.0**. The folder name is misleading; do not identify this kernel as 3.10 based on the directory name.

Device-specific source paths include:

- `linux-3.10/arch/x86/platform/asustek/Z170CG/boardinfo.c`
- `linux-3.10/arch/x86/platform/asustek/Z170CG/boardinfo.h`
- `linux-3.10/arch/x86/boot/dts/xgold/sofia_3gr_soc/`
- `linux-3.10/arch/x86/configs/i386_sofia_defconfig`
- `linux-3.10/arch/x86/configs/x86_64_sofia_defconfig`
- `linux-3.10/drivers/power/ASUS_BATTERY/Z170CG/`

The board information source identifies Z170CG project variants and display-panel identifiers. Touchscreen driver folders also contain ASUS Z170-specific firmware/configuration names.

Both 32-bit and 64-bit SoFIA defconfig files exist in the source package. Their presence alone does **not** establish which bitness or configuration the installed stock firmware actually uses. That must be checked from the stock boot image, kernel config/build properties, or device-side read-only diagnostics before selecting a build target.

## 4. Android source completeness

The `mydroid` directory in this archive is **not a full Android/AOSP source tree**.

The inventory found only 749 files under `mydroid/external`, related to iptables and iproute2. The following normal Android build entry points were not present:

- `mydroid/build/core/main.mk`
- `mydroid/build/envsetup.sh`
- `mydroid/Makefile`

No Z170CG-specific Android `BoardConfig.mk`, `AndroidProducts.mk`, or `device.mk` was found in the inspected archive.

Conclusion: this release gives us the vendor kernel source and a small set of Android external components. It does **not** provide the complete Android framework, device configuration, proprietary vendor blobs, or a ready-to-build full ROM.

## 5. Required next input

Add the **original complete stock firmware package** for the matching WW SKU and exact build to a new GitHub Release asset. The expected package identifier is:

`UL-P01Y-WW-12.26.4.69-user`

Upload the original compressed firmware package as-is; do not expand it and commit its extracted contents to Git. Keep the original untouched.

After it is available, inspect it to identify:

1. Actual boot/recovery/system image formats and contents.
2. Build fingerprint, Android version, ABI/bitness, and kernel version from the installed firmware.
3. Partition names/sizes and update mechanism.
4. Vendor libraries and hardware HAL dependencies.
5. Whether the desired changes can safely be made as a system/app overlay on the stock base, or require a deeper ROM port.

## 6. Project goals (pending implementation)

- Car-friendly launcher/interface.
- Keep navigation and media apps usable.
- Remove or disable only confirmed unnecessary apps, while preserving system dependencies.
- Screen behavior based on USB charging connection/disconnection, subject to available Android permissions and hardware behavior.
- Evaluate newer Android versions only after a target platform, graphics stack, modem, audio, camera, sensors, and vendor compatibility path is proven.

## 7. Flashing and recovery safety

No image should be flashed until the exact tablet variant/SKU, bootloader state, partition layout, image format, and tested recovery procedure are confirmed. The first modification should be reversible and tested without risking the only working boot/recovery path.
