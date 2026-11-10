#!/usr/bin/env python3
"""Create a read-only package inventory from a mounted stock Android system."""
from __future__ import annotations
import argparse
import csv
import json
import pathlib
import re
import subprocess
from datetime import datetime, timezone

def run_aapt(aapt: str, apk: pathlib.Path) -> tuple[str, str]:
    result = subprocess.run(
        [aapt, "dump", "badging", str(apk)],
        capture_output=True, text=True, errors="replace", timeout=45,
    )
    return result.stdout, result.stderr or ("" if result.returncode == 0 else f"aapt exit {result.returncode}")

def parse_badging(text: str) -> dict:
    package = re.search(r"^package: name='([^']*)' versionCode='([^']*)' versionName='([^']*)'", text, re.MULTILINE)
    label = re.search(r"^application-label:'(.*)'$", text, re.MULTILINE)
    min_sdk = re.search(r"^sdkVersion:'([^']*)'", text, re.MULTILINE)
    target_sdk = re.search(r"^targetSdkVersion:'([^']*)'", text, re.MULTILINE)
    permissions = sorted(set(re.findall(r"^uses-permission: name='([^']+)'", text, re.MULTILINE)))
    return {
        "package_name": package.group(1) if package else "",
        "version_code": package.group(2) if package else "",
        "version_name": package.group(3) if package else "",
        "label": label.group(1) if label else "",
        "min_sdk": min_sdk.group(1) if min_sdk else "",
        "target_sdk": target_sdk.group(1) if target_sdk else "",
        "permissions": permissions,
    }

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--system-root", required=True, type=pathlib.Path)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--output-dir", required=True, type=pathlib.Path)
    args = parser.parse_args()
    system_root = args.system_root.resolve()
    out = args.output_dir
    out.mkdir(parents=True, exist_ok=True)
    rows, errors, apk_paths = [], [], []
    for subdir in ("app", "priv-app"):
        folder = system_root / subdir
        if folder.exists():
            apk_paths.extend(folder.rglob("*.apk"))
    for apk in sorted(apk_paths):
        badging, error = run_aapt(args.aapt, apk)
        data = parse_badging(badging)
        relative = apk.relative_to(system_root).as_posix()
        row = {
            **data,
            "partition_path": "/system/" + relative,
            "area": relative.split("/", 1)[0],
            "apk_bytes": apk.stat().st_size,
            "has_odex": any(sibling.exists() for sibling in (
                apk.with_suffix(".odex"),
                apk.parent / (apk.stem + ".odex"),
                apk.parent / "oat" / "x86" / (apk.stem + ".odex"),
            )),
        }
        rows.append(row)
        if error or not data["package_name"]:
            errors.append({"path": row["partition_path"], "error": error, "aapt_output": badging[-1200:]})

    fields = ["package_name", "label", "version_name", "version_code", "min_sdk", "target_sdk", "area", "partition_path", "apk_bytes", "has_odex"]
    with (out / "system-package-inventory.csv").open("w", newline="", encoding="utf-8-sig") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({key: row.get(key, "") for key in fields})
    (out / "system-package-inventory.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    (out / "aapt-errors.json").write_text(json.dumps(errors, ensure_ascii=False, indent=2), encoding="utf-8")

    build_prop = system_root / "build.prop"
    if build_prop.exists():
        (out / "system-build.prop").write_bytes(build_prop.read_bytes())
    top_dirs = []
    for child in sorted(system_root.iterdir()):
        if child.is_dir():
            count = sum(1 for item in child.rglob("*") if item.is_file())
            top_dirs.append({"directory": child.name, "files": count})
    (out / "system-layout.json").write_text(json.dumps(top_dirs, ensure_ascii=False, indent=2), encoding="utf-8")

    report = [
        "# P01Y WW — Stock system image inventory",
        "",
        f"Generated: {datetime.now(timezone.utc).isoformat()}",
        "",
        f"- APK files found under /system/app and /system/priv-app: **{len(apk_paths)}**",
        f"- APKs parsed with aapt: **{sum(bool(row['package_name']) for row in rows)}**",
        f"- APKs requiring manual inspection: **{len(errors)}**",
        "- Package inventory: system-package-inventory.csv and system-package-inventory.json",
        "",
        "## Safety and scope",
        "",
        "- Extracted from the verified original ASUS P01Y WW OTA.",
        "- No APK, framework file, permission file, service, or partition content was changed.",
        "- This is a stock-image inventory; it does not represent user-installed apps on a modified tablet.",
        "- Removal decisions must account for shared UID, privileged permissions, framework dependencies, telephony, audio, Bluetooth, sensors, storage, boot, and ASUS hardware services.",
        "- This report is not a flashable update package.",
        "",
    ]
    (out / "ROM-SYSTEM-INVENTORY.md").write_text("\n".join(report), encoding="utf-8")
    print("\n".join(report))

if __name__ == "__main__":
    main()
