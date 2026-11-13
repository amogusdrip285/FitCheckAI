"""Legacy migration-test illustrations, never dataset photos or APK assets. Requires Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw

DEST = Path(__file__).resolve().parents[1] / "src/test/assets/seed"
DEST.mkdir(parents=True, exist_ok=True)
ITEMS = {
    "t1": ("top", "#F5F5F2"), "t2": ("top", "#1C1C1C"),
    "t3": ("shirt", "#B9CDE5"), "t4": ("shirt", "#FFFFFF"),
    "t5": ("shirt", "#D8C3A5"), "t6": ("top", "#8A8A8A"),
    "b1": ("bottom", "#3B5B8C"), "b2": ("bottom", "#222222"),
    "b3": ("bottom", "#C8B48A"), "b4": ("short", "#6E8DB8"),
    "d1": ("dress", "#151515"), "o1": ("shirt", "#4A6A9A"),
    "o2": ("shirt", "#5E5E62"), "o3": ("shirt", "#B23A30"),
    "s1": ("shoe", "#F0F0F0"), "s2": ("shoe", "#6B4226"),
    "s3": ("shoe", "#2F4F6F"), "a1": ("belt", "#1A1A1A"),
}
for name, (kind, color) in ITEMS.items():
    im = Image.new("RGBA", (384, 384), "#F2EEE7")
    draw = ImageDraw.Draw(im)
    edge = "#514D48"
    def poly(points):
        draw.polygon(points, fill=color, outline=edge, width=3)
    if kind in ("top", "shirt"):
        poly([(140,70),(166,85),(218,85),(244,70),(319,126),(284,180),(250,157),(250,310),(134,310),(134,157),(100,180),(65,126)])
        draw.arc((164,60,220,104), 0, 180, fill=edge, width=4)
        if kind == "shirt":
            draw.line((192,104,192,309), fill=edge, width=3)
            for y in range(126,290,31): draw.ellipse((202,y,207,y+5), fill=edge)
    elif kind in ("bottom", "short"):
        end = 318 if kind == "bottom" else 251
        poly([(117,66),(267,66),(282,end),(211,end),(192,158),(173,end),(102,end)])
        draw.line((119,90,265,90), fill=edge, width=3)
        draw.line((192,91,192,151), fill=edge, width=3)
    elif kind == "dress":
        poly([(146,65),(168,80),(216,80),(238,65),(256,137),(224,170),(290,319),(94,319),(160,170),(128,137)])
        draw.line((159,172,226,172), fill="#9C9183", width=4)
    elif kind == "shoe":
        poly([(80,139),(129,155),(165,147),(217,201),(297,220),(316,250),(314,274),(70,274),(67,218)])
        draw.line((72,257,312,257), fill="#BCB5A9", width=9)
        for x,y in [(164,182),(182,198),(199,213)]: draw.line((x,y,x+23,y-14), fill="#E8E3DB", width=5)
    else:
        draw.rounded_rectangle((55,155,329,218), radius=12, fill=color, outline=edge, width=3)
        draw.rounded_rectangle((85,149,153,224), radius=9, outline="#C4A569", width=10)
        for x in range(220,310,20): draw.ellipse((x,183,x+5,188), fill="#C4A569")
    im.save(DEST / f"seed-{name}.png", optimize=True)
print(f"Wrote {len(ITEMS)} original debug illustrations to {DEST}")
