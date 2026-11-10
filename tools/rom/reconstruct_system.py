#!/usr/bin/env python3
"""Reconstruct the P01Y WW system ext4 image from the verified ASUS block OTA."""
from __future__ import annotations
import argparse
import hashlib
import pathlib
import zipfile

BLOCK = 4096
EXPECTED_SHA256 = "a3cff1f6703cb68cbe189fc029ab0a94fd7a3ab85297cfdd271fe3e7ff16a6a2"
EXPECTED_SIZE = 1_121_160_179

def parse_ranges(payload: str) -> list[tuple[int, int]]:
    values = [int(value) for value in payload.split(",")]
    if not values:
        raise ValueError("Empty range payload")
    count = values[0]
    if count % 2 or len(values) != count + 1:
        raise ValueError(f"Malformed range payload: {payload[:120]}")
    ranges = []
    for index in range(1, len(values), 2):
        start, end = values[index], values[index + 1]
        if start < 0 or end <= start:
            raise ValueError(f"Invalid block range {start}:{end}")
        ranges.append((start, end))
    return ranges

def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def reconstruct(archive: pathlib.Path, output: pathlib.Path) -> dict:
    if not archive.is_file():
        raise FileNotFoundError(archive)
    archive_size = archive.stat().st_size
    if archive_size != EXPECTED_SIZE:
        raise RuntimeError(f"Original OTA size mismatch: expected {EXPECTED_SIZE}, got {archive_size}")
    archive_hash = sha256_file(archive)
    if archive_hash != EXPECTED_SHA256:
        raise RuntimeError(f"Original OTA SHA-256 mismatch: expected {EXPECTED_SHA256}, got {archive_hash}")

    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive, "r") as package:
        bad_member = package.testzip()
        if bad_member:
            raise RuntimeError(f"Outer ZIP CRC check failed at {bad_member}")
        transfer = package.read("system.transfer.list").decode("ascii").splitlines()
        if len(transfer) < 3:
            raise RuntimeError("system.transfer.list is incomplete")
        version = int(transfer[0].strip())
        declared_new_blocks = int(transfer[1].strip())
        if version != 1:
            raise RuntimeError(f"Unsupported transfer-list version {version}; only audited v1 is accepted")

        commands = []
        max_end = 0
        counted_new_blocks = 0
        for line_number, line in enumerate(transfer[2:], start=3):
            line = line.strip()
            if not line:
                continue
            command, separator, payload = line.partition(" ")
            if command not in {"new", "zero", "erase"}:
                raise RuntimeError(f"Unsupported transfer command at line {line_number}: {command}")
            if not separator:
                raise RuntimeError(f"Missing block ranges at line {line_number}")
            ranges = parse_ranges(payload)
            for start, end in ranges:
                max_end = max(max_end, end)
                if command == "new":
                    counted_new_blocks += end - start
            commands.append((command, ranges))

        if counted_new_blocks != declared_new_blocks:
            raise RuntimeError(f"Transfer-list new blocks mismatch: declared {declared_new_blocks}, ranges total {counted_new_blocks}")
        expected_data_bytes = declared_new_blocks * BLOCK
        system_info = package.getinfo("system.new.dat")
        if system_info.file_size != expected_data_bytes:
            raise RuntimeError(f"system.new.dat size mismatch: expected {expected_data_bytes}, got {system_info.file_size}")
        target_bytes = max_end * BLOCK
        if target_bytes <= 0:
            raise RuntimeError("Transfer list produced an empty system image")

        zero_chunk = b"\\0" * (8 * 1024 * 1024)
        consumed = 0
        with package.open("system.new.dat", "r") as data, output.open("w+b") as image:
            image.truncate(target_bytes)
            for command, ranges in commands:
                for start, end in ranges:
                    image.seek(start * BLOCK)
                    remaining = (end - start) * BLOCK
                    if command == "new":
                        while remaining:
                            chunk = data.read(min(len(zero_chunk), remaining))
                            if not chunk:
                                raise RuntimeError("system.new.dat ended inside a new range")
                            image.write(chunk)
                            remaining -= len(chunk)
                            consumed += len(chunk)
                    else:
                        while remaining:
                            chunk_size = min(len(zero_chunk), remaining)
                            image.write(zero_chunk[:chunk_size])
                            remaining -= chunk_size
            if data.read(1):
                raise RuntimeError("Unexpected trailing bytes remain in system.new.dat")
            image.flush()

    with output.open("rb") as image:
        image.seek(1080)
        ext4_magic = image.read(2).hex()
    if ext4_magic != "53ef":
        raise RuntimeError(f"Expected ext4 superblock magic 53ef, found {ext4_magic}")
    image_hash = sha256_file(output)
    output.with_suffix(output.suffix + ".sha256").write_text(f"{image_hash}  {output.name}\n", encoding="ascii")
    return {
        "archive_sha256": archive_hash,
        "archive_size": archive_size,
        "transfer_version": version,
        "declared_new_blocks": declared_new_blocks,
        "consumed_new_bytes": consumed,
        "image_size": target_bytes,
        "image_sha256": image_hash,
        "ext4_magic": ext4_magic,
        "image_path": str(output),
    }

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", required=True, type=pathlib.Path)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    args = parser.parse_args()
    result = reconstruct(args.archive, args.output)
    for key, value in result.items():
        print(f"{key}: {value}")

if __name__ == "__main__":
    main()
