#!/usr/bin/env python3
"""Prepare the user's original ALI CAR wallpaper for Android 5 / 1 GB RAM."""
from pathlib import Path
from PIL import Image, ImageOps

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"111.jfif"
OUTPUT=ROOT/"ali-car-system"/"app"/"src"/"main"/"res"/"drawable-nodpi"/"bg_dashboard.webp"
WIDTH,HEIGHT=1280,720

if not SOURCE.is_file():
    raise SystemExit("FAIL: repository wallpaper 111.jfif is missing")
if not OUTPUT.parent.is_dir():
    raise SystemExit("FAIL: drawable-nodpi folder missing")

with Image.open(SOURCE) as source:
    source.load()
    if source.width < WIDTH or source.height < HEIGHT:
        raise SystemExit(f"FAIL: source wallpaper too small: {source.size}")
    source=source.convert("RGB")
    optimized=ImageOps.fit(source,(WIDTH,HEIGHT),method=Image.Resampling.LANCZOS,centering=(0.5,0.5))
    optimized.save(OUTPUT,"WEBP",quality=88,method=6)

with Image.open(OUTPUT) as check:
    check.load()
    if (check.width,check.height)!=(WIDTH,HEIGHT):
        raise SystemExit("FAIL: optimized wallpaper must be exactly 1280x720")
    if OUTPUT.stat().st_size > 450_000:
        raise SystemExit(f"FAIL: optimized wallpaper is too large: {OUTPUT.stat().st_size} bytes")
print(f"PASS: original 111.jfif optimized to {WIDTH}x{HEIGHT} WebP ({OUTPUT.stat().st_size} bytes)")
