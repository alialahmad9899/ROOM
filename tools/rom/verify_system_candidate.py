#!/usr/bin/env python3
"""Verify a modified P01Y system candidate from a read-only mount."""
from __future__ import annotations

import argparse
import json
import pathlib
import subprocess
import xml.etree.ElementTree as ET


def run_checked(command: list[str]) -> str:
    result = subprocess.run(command, capture_output=True, text=True, errors="replace")
    if result.returncode:
        raise RuntimeError(
            f"Command failed ({result.returncode}): {' '.join(command)}\n"
            f"{result.stdout}\n{result.stderr}"
        )
    return result.stdout


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--system-root", required=True, type=pathlib.Path)
    parser.add_argument("--inventory", required=True, type=pathlib.Path)
    parser.add_argument("--manifest", required=True, type=pathlib.Path)
    parser.add_argument("--report", required=True, type=pathlib.Path)
    parser.add_argument("--backup-archive", required=True, type=pathlib.Path)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    args = parser.parse_args()

    root = args.system_root.resolve()
    inventory = json.loads(args.inventory.read_text(encoding="utf-8"))
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    report = json.loads(args.report.read_text(encoding="utf-8"))
    by_package = {
        row.get("package_name"): row
        for row in inventory
        if row.get("package_name")
    }

    missing_protected = []
    for package in manifest.get("protected_packages", []):
        row = by_package.get(package)
        if not row:
            missing_protected.append({"package": package, "reason": "missing from stock inventory"})
            continue
        relative = pathlib.PurePosixPath(row["partition_path"]).relative_to("/system")
        if not root.joinpath(*relative.parts).is_file():
            missing_protected.append({
                "package": package,
                "reason": "protected APK missing from modified image",
                "path": row["partition_path"],
            })

    unexpected_remaining = []
    for item in report.get("removed_packages", []):
        relative_dir = pathlib.PurePosixPath(item["directory"].lstrip("/"))
        directory = root.joinpath(*relative_dir.parts)
        if directory.exists():
            unexpected_remaining.append({
                "package": item["package"],
                "directory": item["directory"],
            })

    launcher = root / "app" / "ALICarSystem" / "ALICarSystem.apk"
    if not launcher.is_file():
        raise RuntimeError(f"Wesal launcher APK missing at {launcher}")
    badging = run_checked([args.aapt, "dump", "badging", str(launcher)])
    if "package: name='com.ali.carsystem'" not in badging:
        raise RuntimeError("Embedded launcher package ID is incorrect")
    if "sdkVersion:'21'" not in badging:
        raise RuntimeError("Embedded launcher does not declare minSdk 21")

    bootanimation = root / "media" / "bootanimation.zip"
    if not bootanimation.is_file() or bootanimation.stat().st_size < 1024:
        raise RuntimeError("ALI bootanimation.zip is missing or unexpectedly small")
    import zipfile
    with zipfile.ZipFile(bootanimation) as bootzip:
        names = set(bootzip.namelist())
        if "desc.txt" not in names or not any(name.startswith("part0/") for name in names):
            raise RuntimeError("ALI bootanimation.zip has an invalid frame layout")
        desc = bootzip.read("desc.txt").decode("ascii", errors="strict")
        if not desc.startswith("1024 600 "):
            raise RuntimeError("ALI bootanimation resolution does not match 1024x600")

    archive_listing = run_checked(["tar", "-tzf", str(args.backup_archive)])
    missing_backup = []
    for item in report.get("removed_packages", []):
        directory = item["directory"].lstrip("/") + "/"
        if directory not in archive_listing and not any(
            line.startswith(directory) for line in archive_listing.splitlines()
        ):
            missing_backup.append(item["directory"])

    pm_cust = root / "etc" / "cust_pms" / "WW" / "pm_cust.xml"
    hidden_packages = []
    if pm_cust.is_file():
        xml_root = ET.parse(pm_cust).getroot()
        hidden_packages = [
            node.text.strip()
            for node in xml_root.findall(".//string-array[@name='hidden_system_packages']/item")
            if node.text and node.text.strip()
        ]
    stale_hidden_entries = sorted(
        set(hidden_packages).intersection(
            item["package"] for item in report.get("removed_packages", [])
        )
    )

    gmail_migration = root / "etc" / "updatecmds" / "google_generic_update.txt"
    gmail_stanza_remains = (
        gmail_migration.is_file()
        and "com.google.android.gm:com.google.android.providers.gmail"
        in gmail_migration.read_text(encoding="utf-8", errors="ignore")
    )

    result = {
        "read_only_mount": True,
        "protected_package_count": len(manifest.get("protected_packages", [])),
        "missing_protected_packages": missing_protected,
        "removed_package_count": report.get("removed_package_count", 0),
        "unexpected_remaining_removed_directories": unexpected_remaining,
        "embedded_launcher": "/system/app/ALICarSystem/ALICarSystem.apk",
        "embedded_launcher_package": "com.ali.carsystem",
        "bootanimation": "/system/media/bootanimation.zip",
        "bootanimation_verified": bootanimation.is_file(),
        "removed_app_backup_archive": str(args.backup_archive),
        "missing_backup_directories": missing_backup,
        "stale_WW_hidden_list_entries": stale_hidden_entries,
        "gmail_migration_stanza_remains": gmail_stanza_remains,
        "passed": not (
            missing_protected
            or unexpected_remaining
            or missing_backup
            or stale_hidden_entries
            or gmail_stanza_remains
        ),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if not result["passed"]:
        raise SystemExit("Modified system candidate verification failed")


if __name__ == "__main__":
    main()
