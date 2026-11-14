#!/usr/bin/env python3
"""Fetch the verified public-domain Arabic recordings into the APK assets."""
from __future__ import annotations
import hashlib
import json
import pathlib
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
CATALOG_PATH = ROOT / "tools/rom/open_music_catalog.json"
ASSET_DIR = ROOT / "ali-car-system/app/src/main/assets/music"
ASSET_MANIFEST = ROOT / "ali-car-system/app/src/main/assets/open_music_catalog.json"
LICENSE_DOC = ROOT / "ali-car-system/OPEN-MUSIC-LICENSES.md"

def fail(message: str) -> None:
    raise SystemExit("OPEN MUSIC ERROR: " + message)

def main() -> None:
    catalog = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))
    tracks = catalog.get("tracks", [])
    if len(tracks) < 20:
        fail(f"catalog too small: {len(tracks)}")
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    hashes = []
    attribution = [
        "# ALI CAR — Open Arabic Music",
        "",
        "The bundled recordings below are identified as Public Domain in the linked source records.",
        "They are historical Arabic recordings, mainly from the Library of Congress National Jukebox (1917–1921),",
        "plus the early Wikimedia Commons recording noted in its individual record.",
        "Only the audio files listed in this manifest are bundled; no modern copyrighted popular tracks are included.",
        "",
        "| # | Track | Artist / credited source | Licence | Source record |",
        "|---:|---|---|---|---|",
    ]
    for item in tracks:
        filename = item["filename"]
        url = item["audio_url"]
        target = ASSET_DIR / filename
        request = urllib.request.Request(
            url,
            headers={"User-Agent": "ALI-CAR-Open-Music-Builder/1.0 (+https://github.com/alialahmad9899/ROOM)"}
        )
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                data = response.read()
                content_type = response.headers.get("Content-Type", "")
        except Exception as exc:
            fail(f"download failed for {filename}: {exc}")
        if len(data) < 20_000:
            fail(f"download too small for {filename}: {len(data)} bytes")
        if not data.startswith(b"OggS"):
            fail(f"download is not an Ogg stream for {filename}; content-type={content_type}")
        tmp = target.with_suffix(target.suffix + ".tmp")
        tmp.write_bytes(data)
        tmp.replace(target)
        digest = hashlib.sha256(data).hexdigest()
        hashes.append(f"{digest}  {filename}")
        attribution.append(
            f"| {item['track_number']} | {item['title']} | {item['artist']} | Public Domain | "
            f"[source record]({item['source_page']}) |"
        )
        print(f"OPEN_MUSIC {item['track_number']:02d} {filename} bytes={len(data)} sha256={digest}")
    if len(list(ASSET_DIR.glob("*.ogg"))) != len(tracks):
        fail("asset file count mismatch")
    (ASSET_DIR / "SHA256SUMS.txt").write_text("\n".join(hashes) + "\n", encoding="utf-8")
    ASSET_MANIFEST.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    LICENSE_DOC.write_text("\n".join(attribution) + "\n", encoding="utf-8")
    print(f"OPEN_MUSIC_OK tracks={len(tracks)} total_bytes={sum((ASSET_DIR / x['filename']).stat().st_size for x in tracks)}")

if __name__ == "__main__":
    main()
