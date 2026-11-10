#!/usr/bin/env python3
"""Read-only inventory of APKs and build properties in the verified P01Y system OTA."""
from __future__ import annotations

import argparse
import csv
import hashlib
import os
import pathlib
import re
import shutil
import subprocess
import tempfile
import zipfile

BLOCK = 4096

def run(cmd: list[str], *, timeout: int = 120, check: bool = True) -> subprocess.CompletedProcess:
    p = subprocess.run(cmd, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    if check and p.returncode:
        raise RuntimeError(f"Command failed ({p.returncode}): {' '.join(cmd)}\n{p.stdout[-4000:]}")
    return p

def debugfs(image: pathlib.Path, command: str, *, check: bool = True) -> str:
    p = run(["debugfs", "-R", command, str(image)], timeout=180, check=check)
    return p.stdout

def reconstruct(archive: pathlib.Path, image: pathlib.Path, report: list[str]) -> None:
    with zipfile.ZipFile(archive) as z:
        transfer = z.read("system.transfer.list").decode("ascii", "replace").splitlines()
        info = z.getinfo("system.new.dat")
        version = int(transfer[0].strip())
        if version != 1:
            raise RuntimeError(f"Expected transfer-list v1, found {version}")
        declared_blocks = int(transfer[1].strip())
        commands = transfer[2:]
        ranges: list[tuple[int, int]] = []
        max_block = 0
        new_blocks = 0
        for row in commands:
            if not row.strip():
                continue
            cmd, _, payload = row.partition(" ")
            if cmd not in {"new", "zero", "erase"}:
                raise RuntimeError(f"Unsupported transfer-list command: {cmd}")
            if cmd != "new":
                continue
            values = [int(x) for x in payload.split(",")]
            count = values[0]
            if count % 2 or len(values) != count + 1:
                raise RuntimeError(f"Malformed new range: {row}")
            for idx in range(count // 2):
                start, end = values[1 + 2 * idx:3 + 2 * idx]
                if start < 0 or end <= start:
                    raise RuntimeError(f"Invalid new range: {row}")
                ranges.append((start, end))
                new_blocks += end - start
                max_block = max(max_block, end)
        if new_blocks != declared_blocks or info.file_size != declared_blocks * BLOCK:
            raise RuntimeError(
                f"Transfer-list/data mismatch: ranges={new_blocks}, declared={declared_blocks}, "
                f"dat={info.file_size}"
            )
        report += [
            f"transfer_list_version={version}",
            f"declared_new_blocks={declared_blocks}",
            f"new_range_blocks={new_blocks}",
            f"system_new_dat_bytes={info.file_size}",
            f"target_image_bytes={max_block * BLOCK}",
        ]
        with image.open("wb") as out, z.open("system.new.dat") as data:
            out.truncate(max_block * BLOCK)
            consumed = 0
            for start, end in ranges:
                length = (end - start) * BLOCK
                out.seek(start * BLOCK)
                remaining = length
                while remaining:
                    chunk = data.read(min(4 * 1024 * 1024, remaining))
                    if not chunk:
                        raise RuntimeError("system.new.dat ended early")
                    out.write(chunk)
                    remaining -= len(chunk)
                    consumed += len(chunk)
            if data.read(1):
                raise RuntimeError("Unexpected bytes remain in system.new.dat")
        if consumed != info.file_size:
            raise RuntimeError(f"Consumed {consumed} bytes but expected {info.file_size}")
    with image.open("rb") as f:
        f.seek(1080)
        magic = f.read(2).hex()
    report.append(f"ext4_magic_at_1080={magic}")
    if magic != "53ef":
        raise RuntimeError("Reconstructed system image did not pass ext4 magic check")

def extract_file(image: pathlib.Path, source: str, dest: pathlib.Path) -> bool:
    p = run(["debugfs", "-R", f"dump {source} {dest}", str(image)], check=False)
    return p.returncode == 0 and dest.exists() and dest.stat().st_size > 0

def dir_exists(image: pathlib.Path, path: str) -> bool:
    p = run(["debugfs", "-R", f"stat {path}", str(image)], check=False)
    return "Type: directory" in p.stdout

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--archive", required=True, type=pathlib.Path)
    ap.add_argument("--out", required=True, type=pathlib.Path)
    args = ap.parse_args()
    archive = args.archive.resolve()
    outdir = args.out.resolve()
    outdir.mkdir(parents=True, exist_ok=True)
    report = [
        "ASUS Z170CG / P01Y WW STOCK SYSTEM INVENTORY",
        "Mode: READ ONLY; no source firmware files are modified",
        f"archive={archive.name}",
        f"archive_bytes={archive.stat().st_size}",
        f"archive_sha256={hashlib.sha256(archive.read_bytes()).hexdigest()}",
    ]
    image = outdir / "system-reconstructed.img"
    reconstruct(archive, image, report)

    build_prop = outdir / "system-build.prop"
    if extract_file(image, "/build.prop", build_prop) or extract_file(image, "/system/build.prop", build_prop):
        report += ["", "BUILD PROPERTIES (selected):"]
        selected = []
        for line in build_prop.read_text(errors="replace").splitlines():
            if re.match(r"^(ro\.build\.|ro\.product\.|ro\.hardware|ro\.board\.platform|ro\.product\.cpu\.abi)", line):
                selected.append(line)
        report += selected
    else:
        report.append("build.prop extraction failed")

    root_listing = debugfs(image, "ls -l /", check=False)
    (outdir / "system-root-listing.txt").write_text(root_listing, encoding="utf-8")
    report += ["", "SYSTEM ROOT LISTING:", root_listing]

    extracted = outdir / "apk-tree"
    extracted.mkdir(exist_ok=True)
    candidate_dirs = ["/app", "/priv-app", "/vendor/app", "/vendor/priv-app", "/system/app", "/system/priv-app"]
    found_dirs = []
    for source in candidate_dirs:
        if dir_exists(image, source):
            found_dirs.append(source)
            dest = extracted / source.strip("/").replace("/", "__")
            dest.mkdir(parents=True, exist_ok=True)
            p = run(["debugfs", "-R", f"rdump {source} {dest}", str(image)], timeout=600, check=False)
            report.append(f"rdump {source}: exit={p.returncode}; {p.stdout[-800:].strip()}")
    report.append("apk_directories_found=" + ",".join(found_dirs))

    aapt = shutil.which("aapt")
    rows = []
    apk_files = sorted(extracted.rglob("*.apk"))
    for apk in apk_files:
        package = label = version_name = version_code = min_sdk = target_sdk = native_code = ""
        if aapt:
            p = run([aapt, "dump", "badging", str(apk)], timeout=90, check=False)
            if p.returncode == 0:
                for line in p.stdout.splitlines():
                    if line.startswith("package:"):
                        for key, value in re.findall(r"(\w+)='([^']*)'", line):
                            if key == "name": package = value
                            elif key == "versionName": version_name = value
                            elif key == "versionCode": version_code = value
                    elif line.startswith("application-label:"):
                        label = line.partition("'")[2].rsplit("'", 1)[0]
                    elif line.startswith("sdkVersion:"):
                        min_sdk = line.partition("'")[2].rsplit("'", 1)[0]
                    elif line.startswith("targetSdkVersion:"):
                        target_sdk = line.partition("'")[2].rsplit("'", 1)[0]
                    elif line.startswith("native-code:"):
                        native_code = line.partition("'")[2].rsplit("'", 1)[0]
        rows.append({
            "partition_path": "/" + str(apk.relative_to(extracted)).replace(os.sep, "/"),
            "file_name": apk.name,
            "bytes": apk.stat().st_size,
            "package_name": package,
            "application_label": label,
            "version_name": version_name,
            "version_code": version_code,
            "min_sdk": min_sdk,
            "target_sdk": target_sdk,
            "native_code": native_code,
        })

    csv_path = outdir / "system-apk-inventory.csv"
    with csv_path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()) if rows else [
            "partition_path","file_name","bytes","package_name","application_label",
            "version_name","version_code","min_sdk","target_sdk","native_code"
        ])
        writer.writeheader()
        writer.writerows(rows)
    report += [
        "",
        f"apk_count={len(rows)}",
        f"aapt_available={bool(aapt)}",
        "APK INVENTORY CSV: system-apk-inventory.csv",
    ]
    (outdir / "system-inventory-report.txt").write_text("\n".join(report) + "\n", encoding="utf-8")
    print("\n".join(report[-40:]))
    print(f"Inventory rows: {len(rows)}")

if __name__ == "__main__":
    main()
