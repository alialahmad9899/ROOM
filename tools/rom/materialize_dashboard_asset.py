#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageOps
root=Path(__file__).resolve().parents[2]
app=root/"ali-car-system"/"app"/"src"/"main"
source=app/"res"/"drawable-nodpi"/"bg_dashboard.webp"
if not source.is_file(): raise SystemExit("FAIL: background resource was not imported")
with Image.open(source) as im:
    im.load()
    if im.width < 240 or im.height < 140: raise SystemExit("FAIL: source background is too small to upscale safely")
    fitted=ImageOps.fit(im.convert("RGB"),(1280,720),method=Image.Resampling.LANCZOS,centering=(0.5,0.52))
    fitted.save(source,"WEBP",quality=84,method=6)
with Image.open(source) as check:
    check.verify()
with Image.open(source) as check:
    check.load()
    if (check.width,check.height)!=(1280,720): raise SystemExit("FAIL: dashboard image dimensions are not 1280x720")
print("PASS: dashboard WebP fully decodes at 1280x720")
