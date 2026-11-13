#!/usr/bin/env python3
"""Render a sharp 1280x720 ALI CAR night-drive wallpaper for low-RAM tablets."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import random

ROOT=Path(__file__).resolve().parents[2]
APP=ROOT/"ali-car-system"/"app"/"src"/"main"
OUTPUT=APP/"res"/"drawable-nodpi"/"bg_dashboard.webp"
W,H=1280,720
rng=random.Random(23)

def blend(a,b,t):
    return tuple(int(a[i]*(1-t)+b[i]*t) for i in range(3))

def render():
    image=Image.new("RGB",(W,H))
    px=image.load()
    top=(7,20,35);horizon=(23,72,98);bottom=(3,10,19)
    for y in range(H):
        t=y/float(H)
        col=blend(top,horizon,min(1,t/.56)) if t<.56 else blend(horizon,bottom,(t-.56)/.44)
        for x in range(W):px[x,y]=col

    glow=Image.new("RGBA",(W,H),(0,0,0,0));g=ImageDraw.Draw(glow)
    g.ellipse((260,205,1030,620),fill=(37,142,178,50))
    g.ellipse((440,285,850,520),fill=(255,171,94,48))
    glow=glow.filter(ImageFilter.GaussianBlur(74));image=Image.alpha_composite(image.convert("RGBA"),glow)

    ridge=Image.new("RGBA",(W,H),(0,0,0,0));d=ImageDraw.Draw(ridge)
    d.polygon([(0,395),(115,328),(250,374),(377,296),(510,360),(666,279),(812,352),(954,301),(1094,367),(1280,312),(1280,526),(0,526)],fill=(12,36,53,228))
    d.polygon([(0,432),(168,366),(322,416),(486,343),(631,416),(795,347),(972,426),(1117,365),(1280,423),(1280,570),(0,570)],fill=(7,24,40,246))
    image=Image.alpha_composite(image,ridge)

    city=Image.new("RGBA",(W,H),(0,0,0,0));cd=ImageDraw.Draw(city)
    for _ in range(86):
        x=rng.randint(0,W);bw=rng.randint(7,23);bh=rng.randint(20,100);y=438-bh
        cd.rectangle((x,y,x+bw,459),fill=(5,19,31,222))
        for wy in range(y+7,454,12):
            for wx in range(x+3,x+bw-2,7):
                if rng.random()<.33:
                    cd.rectangle((wx,wy,wx+2,wy+4),fill=rng.choice([(255,184,105,215),(84,210,238,180),(230,240,248,150)]))
    for _ in range(360):
        x=rng.randrange(W);y=rng.randrange(398,468);r=rng.choice((1,1,2))
        cd.ellipse((x-r,y-r,x+r,y+r),fill=rng.choice([(255,176,93,195),(80,207,237,165),(232,242,250,135)]))
    image=Image.alpha_composite(image,city)

    road=Image.new("RGBA",(W,H),(0,0,0,0));rd=ImageDraw.Draw(road)
    rd.polygon([(603,382),(677,382),(1190,720),(90,720)],fill=(6,17,28,238))
    for width,alpha in ((18,24),(9,54),(3,185)):
        rd.line([(603,383),(90,720)],fill=(64,207,239,alpha),width=width)
        rd.line([(677,383),(1190,720)],fill=(64,207,239,alpha),width=width)
    for k in range(6):
        y1=412+k*48;y2=y1+18+k*7;half1=2+k*1.2;half2=3+k*1.8
        rd.polygon([(640-half1,y1),(640+half1,y1),(640+half2,y2),(640-half2,y2)],fill=(184,231,245,155))
    rd.line([(356,510),(206,720)],fill=(255,177,101,48),width=6)
    rd.line([(930,496),(1080,720)],fill=(66,187,224,55),width=7)
    image=Image.alpha_composite(image,road)

    lamps=Image.new("RGBA",(W,H),(0,0,0,0));ld=ImageDraw.Draw(lamps)
    for x,y in ((174,384),(315,401),(954,390),(1110,376)):
        ld.ellipse((x-17,y-17,x+17,y+17),fill=(255,185,110,78))
        ld.ellipse((x-4,y-4,x+4,y+4),fill=(255,230,191,235))
    lamps=lamps.filter(ImageFilter.GaussianBlur(13));image=Image.alpha_composite(image,lamps)

    cockpit=Image.new("RGBA",(W,H),(0,0,0,0));dd=ImageDraw.Draw(cockpit)
    dd.polygon([(0,620),(150,588),(350,603),(520,579),(760,584),(980,600),(1150,575),(1280,606),(1280,720),(0,720)],fill=(2,8,15,231))
    dd.line([(0,620),(150,588),(350,603),(520,579),(760,584),(980,600),(1150,575),(1280,606)],fill=(55,164,198,115),width=3)
    dd.line([(0,631),(1280,617)],fill=(27,85,112,92),width=2)
    for x,side in ((34,1),(1246,-1)):
        dd.line([(x,132),(x,210),(x+side*34,210)],fill=(82,205,238,74),width=2)
        dd.line([(x,242),(x+side*20,242)],fill=(82,205,238,60),width=2)
    image=Image.alpha_composite(image,cockpit)

    vignette=Image.new("RGBA",(W,H),(0,0,0,0));vd=ImageDraw.Draw(vignette)
    for i in range(6):
        inset=18+i*9;alpha=9+i*3
        vd.rounded_rectangle((inset,inset,W-inset,H-inset),radius=130,outline=(0,0,0,alpha),width=26)
    vignette=vignette.filter(ImageFilter.GaussianBlur(44))
    image=Image.alpha_composite(image,vignette).convert("RGB")
    return image

if not OUTPUT.parent.is_dir():
    raise SystemExit("FAIL: drawable-nodpi folder missing")
image=render()
image.save(OUTPUT,"WEBP",quality=90,method=6)
with Image.open(OUTPUT) as check:
    check.load()
    if (check.width,check.height)!=(W,H):
        raise SystemExit("FAIL: wallpaper must be 1280x720")
print("PASS: crisp 1280x720 generated ALI CAR background")
