#!/usr/bin/env python3
"""Ensure the APK contains no bundled music archive.

Only the user's own local files, or files they have the right to use, are scanned
by the music player. Commercial recordings are never downloaded into the APK.
"""
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
ASSET_DIR = ROOT / "ali-car-system/app/src/main/assets/music"
MANIFEST = ROOT / "ali-car-system/app/src/main/assets/open_music_catalog.json"
LICENSE_DOC = ROOT / "ali-car-system/OPEN-MUSIC-LICENSES.md"

def main() -> None:
    if ASSET_DIR.exists():
        shutil.rmtree(ASSET_DIR)
    for path in (MANIFEST, LICENSE_DOC):
        if path.exists():
            path.unlink()
    remaining = list((ROOT / "ali-car-system/app/src/main/assets").rglob("*.mp3"))
    if remaining:
        raise SystemExit("FAIL: unexpected bundled MP3 files: " + ", ".join(map(str, remaining)))
    print("MUSIC_ASSETS_OK bundled_mp3=0 policy=local-user-library-only")

if __name__ == "__main__":
    main()
