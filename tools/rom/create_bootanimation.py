#!/usr/bin/env python3
"""Generate a small ALI boot animation for the P01Y 1024x600 display."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import math
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "workspace" / "rom-output" / "bootanimation.zip"
FRAME_DIR = ROOT / "workspace" / "bootanimation-frames"
FRAME_DIR.mkdir(parents=True, exist_ok=True)
W, H = 1024, 600
FONT_DIR = Path("/usr/share/fonts/truetype/dejavu")
FONT = ImageFont.truetype(str(FONT_DIR / "DejaVuSans-Bold.ttf"), 126)
SUB = ImageFont.truetype(str(FONT_DIR / "DejaVuSans.ttf"), 30)
SMALL = ImageFont.truetype(str(FONT_DIR / "DejaVuSans.ttf"), 20)

def centered(draw, y, text, font, fill):
    box = draw.textbbox((0, 0), text, font=font)
    draw.text(((W - (box[2]-box[0]))/2, y), text, font=font, fill=fill)

for i in range(24):
    im = Image.new("RGB", (W, H), (8, 13, 19))
    px = im.load()
    # Soft radial blue-green glow behind the wordmark.
    for y in range(H):
        for x in range(W):
            dx=(x-W/2)/(W/2); dy=(y-H/2)/(H/2)
            d=math.sqrt(dx*dx+dy*dy)
            glow=max(0.0,1.0-d)*0.13
            px[x,y]=(8, int(13+20*glow), int(19+24*glow))
    draw=ImageDraw.Draw(im)
    alpha=min(1.0, (i+1)/7.0)
    c=(int(66*alpha),int(217*alpha),int(208*alpha))
    centered(draw, 175, "ALI", FONT, c)
    centered(draw, 330, "AL-AHMAD  |  VAO", SUB, (235,242,247))
    centered(draw, 400, "CAR SYSTEM", SMALL, (126,154,169))
    # Thin luminous line.
    draw.rounded_rectangle((390,470,634,474),radius=2,fill=c)
    im.save(FRAME_DIR / f"{i:02d}.png", optimize=True)

OUT.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_STORED) as z:
    z.writestr("desc.txt", "1024 600 15\np 1 0 part0\n")
    for p in sorted(FRAME_DIR.glob("*.png")):
        z.write(p, f"part0/{p.name}")
print(f"created {OUT} ({OUT.stat().st_size} bytes)")
