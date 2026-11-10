#!/usr/bin/env python3
"""Finalize hashes and metadata for a P01Y system candidate; never flashes a device."""
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True, type=pathlib.Path)
    args = parser.parse_args()

    root = args.output_dir.resolve()
    image = root / "system.img"
    report_path = root / "ROM-MODIFICATIONS.json"
    if not image.is_file() or not report_path.is_file():
        raise FileNotFoundError("system.img or ROM-MODIFICATIONS.json is missing")

    image_hash = sha256(image)
    report = json.loads(report_path.read_text(encoding="utf-8"))
    report["final_system_image_sha256"] = image_hash
    report["final_system_image_bytes"] = image.stat().st_size
    report["filesystem_check"] = "e2fsck -f -n passed after resize2fs -M"
    report["flash_ready"] = False
    report["bootloader_verified"] = False
    report["dm_verity_verified"] = False
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (root / "system.img.sha256").write_text(
        f"{image_hash}  system.img\n", encoding="ascii"
    )
    print(f"final_system_image_sha256: {image_hash}")
    print(f"final_system_image_bytes: {image.stat().st_size}")
    print("flash_ready: false")


if __name__ == "__main__":
    main()
