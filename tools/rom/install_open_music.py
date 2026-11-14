#!/usr/bin/env python3
"""Fetch the verified public-domain Arabic recordings into the APK assets."""
from __future__ import annotations
import hashlib
import json
import pathlib
import subprocess
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
    source_dir = ROOT / "offline-map-build" / "open-music-source"
    source_dir.mkdir(parents=True, exist_ok=True)
    hashes = []
    attribution = [
        "# ALI CAR — Open Arabic Music",
        "",
        "Thirty historical Arabic recordings are bundled as MP3 for API 21 compatibility.",
        "The source catalogue identifies each original recording as Public Domain.",
        "The catalogue includes historical Library of Congress National Jukebox recordings",
        "from 1917–1921 and an early Wikimedia Commons recording.",
        "The source Ogg files are transcoded without adding restrictions to the public-domain works.",
        "",
        "| # | Track | Artist / credited source | Licence | Source record |",
        "|---:|---|---|---|---|",
    ]
    for item in tracks:
        ogg_url = item["audio_url"]
        mp3_name = pathlib.Path(item["filename"]).with_suffix(".mp3").name
        mp3_target = ASSET_DIR / mp3_name
        source_target = source_dir / pathlib.Path(item["filename"]).name
        request = urllib.request.Request(
            ogg_url,
            headers={"User-Agent": "ALI-CAR-Open-Music-Builder/1.0 (+https://github.com/alialahmad9899/ROOM)"}
        )
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                source_data = response.read()
                content_type = response.headers.get("Content-Type", "")
        except Exception as exc:
            fail(f"download failed for {item['title']}: {exc}")
        if len(source_data) < 20_000 or not source_data.startswith(b"OggS"):
            fail(f"source is not a complete Ogg audio file for {item['title']}; bytes={len(source_data)} type={content_type}")
        source_target.write_bytes(source_data)
        command = [
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
            "-i", str(source_target), "-vn",
            "-codec:a", "libmp3lame", "-b:a", "96k",
            "-ar", "44100", "-ac", "2",
            "-metadata", f"title={item['title']}",
            "-metadata", f"artist={item['artist']}",
            "-metadata", "album=Open Arabic Historical Archive",
            "-metadata", "genre=Arabic Traditional",
            str(mp3_target),
        ]
        try:
            subprocess.run(command, check=True, timeout=120)
        except Exception as exc:
            fail(f"MP3 conversion failed for {item['title']}: {exc}")
        mp3_data = mp3_target.read_bytes() if mp3_target.is_file() else b""
        if len(mp3_data) < 20_000 or not (mp3_data.startswith(b"ID3") or mp3_data[0] == 0xFF):
            fail(f"converted MP3 is invalid for {item['title']}; bytes={len(mp3_data)}")
        digest = hashlib.sha256(mp3_data).hexdigest()
        hashes.append(f"{digest}  {mp3_name}")
        item["filename"] = mp3_name
        item["source_format"] = "Ogg Opus"
        item["bundled_format"] = "MP3 CBR 96 kbps, 44.1 kHz, stereo"
        attribution.append(
            f"| {item['track_number']} | {item['title']} | {item['artist']} | Public Domain | "
            f"[source record]({item['source_page']}) |"
        )
        print(f"OPEN_MUSIC {item['track_number']:02d} {mp3_name} bytes={len(mp3_data)} sha256={digest}")
    for old in ASSET_DIR.glob("*.ogg"):
        old.unlink()
    if len(list(ASSET_DIR.glob("*.mp3"))) != len(tracks):
        fail("asset MP3 file count mismatch")
    (ASSET_DIR / "SHA256SUMS.txt").write_text("\n".join(hashes) + "\n", encoding="utf-8")
    ASSET_MANIFEST.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    LICENSE_DOC.write_text("\n".join(attribution) + "\n", encoding="utf-8")
    total = sum((ASSET_DIR / x["filename"]).stat().st_size for x in tracks)
    print(f"OPEN_MUSIC_OK tracks={len(tracks)} mp3_bytes={total}")

if __name__ == "__main__":
    main()
