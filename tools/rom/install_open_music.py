#!/usr/bin/env python3
"""Ensure the APK contains no bundled or historical music files."""
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
ASSET_DIR = ROOT / "ali-car-system/app/src/main/assets/music"
MANIFEST = ROOT / "ali-car-system/app/src/main/assets/open_music_catalog.json"
LICENSE_DOC = ROOT / "ali-car-system/OPEN-MUSIC-LICENSES.md"
AUDIO_EXTENSIONS = {
    ".mp3", ".ogg", ".wav", ".m4a", ".aac", ".flac",
    ".opus", ".wma", ".aiff", ".ape", ".mid", ".midi"
}

def main() -> None:
    if ASSET_DIR.exists():
        shutil.rmtree(ASSET_DIR)
    for path in (MANIFEST, LICENSE_DOC):
        if path.exists():
            path.unlink()
    assets = ROOT / "ali-car-system/app/src/main/assets"
    remaining = [path for path in assets.rglob("*")
                 if path.is_file() and path.suffix.lower() in AUDIO_EXTENSIONS]
    if remaining:
        raise SystemExit("FAIL: bundled audio is prohibited: "
                         + ", ".join(str(path) for path in remaining))
    print("MUSIC_ASSETS_OK bundled_audio=0 policy=local-user-library-only")

if __name__ == "__main__":
    main()
