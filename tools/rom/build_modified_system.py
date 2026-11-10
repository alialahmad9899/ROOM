#!/usr/bin/env python3
"""Apply a small explicit debloat manifest to an already-mounted P01Y system image."""
from __future__ import annotations

import argparse
import difflib
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
from datetime import datetime, timezone


PROTECTED_PREFIXES = ("com.android.", "android.")
PROTECTED_CORE = {
    "com.google.android.gms", "com.google.android.gsf", "com.google.android.gsf.login",
    "com.google.android.apps.maps", "com.google.android.tts", "com.google.android.webview",
    "com.asus.webview", "com.android.vending", "com.android.location.fused",
    "com.android.phone", "com.android.server.telecom", "com.android.providers.telephony",
    "com.intel.internal.telephony.OemTelephonyApp", "com.android.bluetooth",
    "com.asus.music", "com.android.settings", "com.android.systemui",
    "com.asus.sensorapi.service", "com.asus.inputdevices", "com.asus.powersaver",
    "com.asus.mobilemanagerservice", "com.asus.server.azs", "com.asus.launcher",
    "com.asus.filemanager", "com.asus.supernote", "com.amazon.kindle",
    "com.zinio.mobile.android.reader", "com.asus.gallery", "com.asus.contacts",
    "com.asus.message",
}


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run_checked(command: list[str]) -> str:
    result = subprocess.run(command, capture_output=True, text=True, errors="replace")
    if result.returncode:
        raise RuntimeError(
            f"Command failed ({result.returncode}): {' '.join(command)}\n"
            f"{result.stdout}\n{result.stderr}"
        )
    return result.stdout


def edit_text_file(
    system_root: pathlib.Path,
    output_root: pathlib.Path,
    relative_path: str,
    transform,
) -> dict | None:
    source = system_root / relative_path
    if not source.is_file():
        return None
    old = source.read_text(encoding="utf-8", errors="strict")
    new = transform(old)
    if new == old:
        return None
    before = output_root / "original-configs" / relative_path
    before.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, before)
    source.write_text(new, encoding="utf-8", newline="")
    diff = "".join(difflib.unified_diff(
        old.splitlines(keepends=True),
        new.splitlines(keepends=True),
        fromfile="a/" + relative_path,
        tofile="b/" + relative_path,
    ))
    return {
        "path": "/" + relative_path,
        "before_sha256": hashlib.sha256(old.encode("utf-8")).hexdigest(),
        "after_sha256": hashlib.sha256(new.encode("utf-8")).hexdigest(),
        "diff": diff,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--system-root", required=True, type=pathlib.Path)
    parser.add_argument("--system-image", required=True, type=pathlib.Path)
    parser.add_argument("--inventory", required=True, type=pathlib.Path)
    parser.add_argument("--candidate-audit", required=True, type=pathlib.Path)
    parser.add_argument("--manifest", required=True, type=pathlib.Path)
    parser.add_argument("--launcher-apk", required=True, type=pathlib.Path)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--output-dir", required=True, type=pathlib.Path)
    args = parser.parse_args()

    system_root = args.system_root.resolve()
    image = args.system_image.resolve()
    out = args.output_dir
    out.mkdir(parents=True, exist_ok=True)

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    inventory = json.loads(args.inventory.read_text(encoding="utf-8"))
    audit = json.loads(args.candidate_audit.read_text(encoding="utf-8"))
    by_package = {row.get("package_name"): row for row in inventory if row.get("package_name")}
    audit_by_package = {row.get("package"): row for row in audit}

    remove_entries = manifest.get("remove", [])
    remove_names = [item["package"] for item in remove_entries]
    if len(remove_names) != len(set(remove_names)):
        raise RuntimeError("Duplicate package appears in the removal manifest")

    protected = set(manifest.get("protected_packages", [])) | PROTECTED_CORE
    conflicts = sorted(protected.intersection(remove_names))
    if conflicts:
        raise RuntimeError("Protected packages included in removal list: " + ", ".join(conflicts))

    rows_to_remove = []
    directories = {}
    for entry in remove_entries:
        package = entry["package"]
        row = by_package.get(package)
        if not row:
            raise RuntimeError(f"Removal package is missing from stock inventory: {package}")
        audit_row = audit_by_package.get(package)
        if not audit_row:
            raise RuntimeError(f"Manifest audit missing for removal package: {package}")
        shared_uid = audit_row.get("shared_user_id") or ""
        if shared_uid:
            raise RuntimeError(
                f"Refusing to remove shared-UID package {package} ({shared_uid})"
            )
        if audit_row.get("persistent") in ("true", "1", "True"):
            raise RuntimeError(f"Refusing to remove persistent package: {package}")

        apk_relative = pathlib.PurePosixPath(row["partition_path"].lstrip("/"))
        if len(apk_relative.parts) < 3 or apk_relative.parts[0] != "system":
            raise RuntimeError(f"Unexpected package path for {package}: {apk_relative}")
        if apk_relative.parts[1] not in ("app", "priv-app"):
            raise RuntimeError(f"Refusing to remove package outside app directories: {package}")
        package_dir_relative = pathlib.PurePosixPath(*apk_relative.parts[1:-1])
        package_dir = system_root.joinpath(*package_dir_relative.parts)
        apk_path = system_root.joinpath(*apk_relative.parts[1:])
        if not apk_path.is_file() or not package_dir.is_dir():
            raise RuntimeError(f"APK or app folder does not exist for {package}: {apk_path}")
        if system_root not in package_dir.resolve().parents:
            raise RuntimeError(f"Package directory escapes system root: {package_dir}")

        directory_key = package_dir_relative.as_posix()
        directories.setdefault(directory_key, []).append(package)
        rows_to_remove.append({
            "package": package,
            "label": row.get("label", ""),
            "apk_path": "/" + apk_relative.as_posix(),
            "directory": "/" + directory_key,
            "apk_bytes": int(row.get("apk_bytes", 0)),
            "reason": entry.get("reason", ""),
        })

    collisions = {path: pkgs for path, pkgs in directories.items() if len(pkgs) > 1}
    if collisions:
        raise RuntimeError(f"Multiple removal entries share an app directory: {collisions}")

    all_packages_by_dir = {}
    for row in inventory:
        package_path = pathlib.PurePosixPath(row["partition_path"].lstrip("/"))
        if len(package_path.parts) >= 3 and package_path.parts[0] == "system":
            directory = pathlib.PurePosixPath(*package_path.parts[1:-1]).as_posix()
            all_packages_by_dir.setdefault(directory, set()).add(row.get("package_name", ""))
    for directory, removing in directories.items():
        remaining = all_packages_by_dir.get(directory, set()) - set(removing)
        if remaining:
            raise RuntimeError(
                f"Refusing to remove folder {directory}; other packages share it: {sorted(remaining)}"
            )

    # Preserve every complete app folder, including odex/lib/resource files, before removal.
    path_list = out / "removed-app-directories.txt"
    path_list.write_text(
        "".join(path + "\n" for path in sorted(directories)),
        encoding="utf-8",
    )
    restore_archive = out / "removed-system-apps.tar.gz"
    run_checked([
        "tar", "--xattrs", "--selinux", "--acls", "--numeric-owner",
        "-czf", str(restore_archive),
        "-C", str(system_root),
        "--files-from", str(path_list),
    ])
    run_checked(["tar", "-tzf", str(restore_archive)])

    for directory in sorted(directories, key=lambda value: value.count("/"), reverse=True):
        target = system_root.joinpath(*pathlib.PurePosixPath(directory).parts)
        shutil.rmtree(target)

    removed_set = set(remove_names)
    config_changes = []

    def clean_ww_hidden_list(text: str) -> str:
        output = []
        for line in text.splitlines(keepends=True):
            match = re.search(r"<item>\s*([^<\s]+)\s*</item>", line)
            if match and match.group(1) in removed_set:
                continue
            output.append(line)
        return "".join(output)

    ww_change = edit_text_file(
        system_root, out, "etc/cust_pms/WW/pm_cust.xml", clean_ww_hidden_list
    )
    if ww_change:
        config_changes.append(ww_change)

    def clean_gmail_migration(text: str) -> str:
        return re.sub(
            r"(?m)^com\.google\.android\.gm:[^\r\n]*\r?\n[ \t]*databases[ \t]*\r?\n?",
            "",
            text,
        )

    gmail_change = edit_text_file(
        system_root, out, "etc/updatecmds/google_generic_update.txt",
        clean_gmail_migration,
    )
    if gmail_change:
        config_changes.append(gmail_change)

    apk = args.launcher_apk.resolve()
    if not apk.is_file():
        raise FileNotFoundError(apk)
    badging = run_checked([args.aapt, "dump", "badging", str(apk)])
    package_match = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE)
    min_sdk_match = re.search(r"^sdkVersion:'(\d+)'", badging, re.MULTILINE)
    if not package_match or package_match.group(1) != "com.wesal.carlauncher":
        raise RuntimeError("Car launcher APK package ID is not com.wesal.carlauncher")
    if not min_sdk_match or int(min_sdk_match.group(1)) > 21:
        raise RuntimeError("Car launcher APK does not support Android API 21")

    launcher_dir = system_root / "app" / "WesalCarLauncher"
    if launcher_dir.exists():
        raise RuntimeError(f"Launcher destination already exists: {launcher_dir}")
    launcher_dir.mkdir(parents=True)
    launcher_dest = launcher_dir / "WesalCarLauncher.apk"
    shutil.copy2(apk, launcher_dest)
    os.chown(launcher_dir, 0, 0)
    os.chown(launcher_dest, 0, 0)
    os.chmod(launcher_dir, 0o755)
    os.chmod(launcher_dest, 0o644)
    try:
        subprocess.run(
            ["chcon", "-R", "u:object_r:system_file:s0", str(launcher_dir)],
            check=True, capture_output=True, text=True,
        )
        selinux_context = "u:object_r:system_file:s0"
    except (FileNotFoundError, subprocess.CalledProcessError):
        # Android's init restorecon uses the device's own /system file_contexts on boot.
        selinux_context = "restorecon-on-device-required"

    config_diff = []
    for change in config_changes:
        config_diff.append(change["diff"])
    (out / "config-changes.patch").write_text(
        "\n".join(config_diff), encoding="utf-8"
    )

    total_apk_bytes = sum(row["apk_bytes"] for row in rows_to_remove)
    report = {
        "generated_utc": datetime.now(timezone.utc).isoformat(),
        "device": manifest["device"],
        "sku": manifest["sku"],
        "base_firmware": manifest["base_firmware"],
        "android": manifest["android"],
        "original_system_image_sha256": sha256_file(image),
        "removed_package_count": len(rows_to_remove),
        "removed_apk_bytes": total_apk_bytes,
        "removed_apk_mib": round(total_apk_bytes / 1048576, 2),
        "removed_packages": rows_to_remove,
        "modified_configs": [
            {key: value for key, value in change.items() if key != "diff"}
            for change in config_changes
        ],
        "launcher": {
            "package": package_match.group(1),
            "apk_bytes": apk.stat().st_size,
            "path": "/system/app/WesalCarLauncher/WesalCarLauncher.apk",
            "min_sdk": int(min_sdk_match.group(1)),
            "file_context": selinux_context,
        },
        "protected_packages": sorted(protected),
        "note": "Candidate system image only. Bootloader, dm-verity and exact device partition size still require read-only device verification before flashing.",
    }
    (out / "ROM-MODIFICATIONS.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps({
        "removed_package_count": len(rows_to_remove),
        "removed_apk_mib": report["removed_apk_mib"],
        "launcher_package": package_match.group(1),
        "launcher_bytes": apk.stat().st_size,
        "modified_config_count": len(config_changes),
        "backup_archive": str(restore_archive),
        "report": str(out / "ROM-MODIFICATIONS.json"),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
