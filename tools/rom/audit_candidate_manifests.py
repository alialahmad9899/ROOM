#!/usr/bin/env python3
"""Capture static AndroidManifest metadata for proposed removals; never changes system files."""
from __future__ import annotations
import argparse
import csv
import json
import pathlib
import re
import subprocess

TEXT_SUFFIXES = {".xml", ".rc", ".conf", ".prop", ".txt", ".sh", ".cfg"}
COMPONENTS = ("activity", "activity-alias", "service", "receiver", "provider")

def run_tree(aapt: str, apk: pathlib.Path) -> tuple[str, int]:
    result = subprocess.run(
        [aapt, "dump", "xmltree", str(apk), "AndroidManifest.xml"],
        capture_output=True, text=True, errors="replace", timeout=60,
    )
    combined = result.stdout + ("\n[AAPT STDERR]\n" + result.stderr if result.stderr else "")
    return combined, result.returncode

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--system-root", required=True, type=pathlib.Path)
    parser.add_argument("--inventory", required=True, type=pathlib.Path)
    parser.add_argument("--candidate-list", required=True, type=pathlib.Path)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--output-dir", required=True, type=pathlib.Path)
    args = parser.parse_args()

    system_root = args.system_root.resolve()
    out = args.output_dir
    raw_dir = out / "raw-manifests"
    raw_dir.mkdir(parents=True, exist_ok=True)
    inventory = json.loads(args.inventory.read_text(encoding="utf-8"))
    by_package = {row.get("package_name"): row for row in inventory if row.get("package_name")}
    candidates = [
        line.strip() for line in args.candidate_list.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]

    summaries, missing = [], []
    for package in candidates:
        item = by_package.get(package)
        if not item:
            missing.append({"package": package, "reason": "Not parsed in APK inventory"})
            continue
        relative = item["partition_path"].removeprefix("/system/")
        apk = system_root / relative
        if not apk.is_file():
            missing.append({"package": package, "reason": "APK path missing", "path": str(apk)})
            continue

        xmltree, exit_code = run_tree(args.aapt, apk)
        safe_name = re.sub(r"[^A-Za-z0-9_.-]+", "_", package)
        manifest_file = f"raw-manifests/{safe_name}.xmltree.txt"
        (raw_dir / f"{safe_name}.xmltree.txt").write_text(xmltree, encoding="utf-8")
        shared_uid = re.search(r'android:sharedUserId[^=]*="([^"]+)"', xmltree)
        persistent = re.search(r'android:persistent[^=]*="([^"]+)"', xmltree)
        names = sorted(set(re.findall(r'android:name[^=]*="([^"]+)"', xmltree)))
        component_counts = {
            kind: len(re.findall(
                r"^\s*E: " + re.escape(kind) + r"(?:\s|$)",
                xmltree, re.MULTILINE,
            ))
            for kind in COMPONENTS
        }
        summaries.append({
            "package": package,
            "label": item.get("label", ""),
            "path": item["partition_path"],
            "apk_bytes": item.get("apk_bytes", 0),
            "shared_user_id": shared_uid.group(1) if shared_uid else "",
            "persistent": persistent.group(1) if persistent else "",
            "component_counts": component_counts,
            "manifest_aapt_exit": exit_code,
            "manifest_file": manifest_file,
            "requested_permissions": item.get("permissions", []),
            "manifest_name_attributes": names,
        })

    config_references = []
    etc_root = system_root / "etc"
    if etc_root.exists():
        for path in etc_root.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
                continue
            try:
                content = path.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                continue
            for package in candidates:
                if package in content:
                    config_references.append({
                        "package": package,
                        "file": "/" + path.relative_to(system_root).as_posix(),
                    })

    config_copy_root = out / "config-references"
    for reference in config_references:
        source = system_root / reference["file"].lstrip("/")
        if source.is_file():
            destination = config_copy_root / reference["file"].lstrip("/")
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(source.read_bytes())

    (out / "candidate-manifest-audit.json").write_text(
        json.dumps(summaries, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (out / "candidate-manifest-missing.json").write_text(
        json.dumps(missing, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    with (out / "candidate-config-references.csv").open(
        "w", newline="", encoding="utf-8-sig"
    ) as stream:
        writer = csv.DictWriter(stream, fieldnames=["package", "file"])
        writer.writeheader()
        writer.writerows(config_references)

    fields = [
        "package", "label", "path", "apk_bytes", "shared_user_id", "persistent",
        "activity", "activity-alias", "service", "receiver", "provider", "manifest_aapt_exit",
    ]
    with (out / "candidate-manifest-audit.csv").open(
        "w", newline="", encoding="utf-8-sig"
    ) as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for item in summaries:
            row = {key: item.get(key, "") for key in fields}
            row.update(item["component_counts"])
            writer.writerow(row)

    print(f"Candidates requested: {len(candidates)}")
    print(f"Manifests captured: {len(summaries)}")
    print(f"Missing or unparsed: {len(missing)}")
    print(f"Explicit /system/etc package references: {len(config_references)}")
    print("No files in the system image were changed.")

if __name__ == "__main__":
    main()
