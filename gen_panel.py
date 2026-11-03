import json
from PIL import Image, ImageDraw, ImageFont, ImageChops

K = 4
FB = "/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf"

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))

def glass(w, h, r, top, bot, edge, edge_w):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    for y in range(h):
        ld.line([(0, y), (w, y)], fill=lerp(top, bot, y / max(1, h - 1)))
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w - 1, h - 1], radius=r, fill=255)
    im.paste(layer, (0, 0), m)
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=r, outline=edge, width=edge_w)
    hl = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    hd = ImageDraw.Draw(hl)
    for i in range(int(h * 0.35)):
        a = int(70 * (1 - i / (h * 0.35)))
        hd.line([(r // 2, edge_w + 1 + i), (w - r // 2, edge_w + 1 + i)], fill=(170, 225, 255, a))
    hl.putalpha(ImageChops.multiply(hl.getchannel('A'), m))
    im = Image.alpha_composite(im, hl)
    return im

def icon_pof(d, cx, cy, u):
    pts = [(-11, 7), (-11, -6), (-5, 0), (0, -9), (5, 0), (11, -6), (11, 7)]
    d.polygon([(cx + x * u, cy + y * u) for x, y in pts], fill=(255, 255, 255, 255))
    d.rounded_rectangle([cx - 11 * u, cy + 9 * u, cx + 11 * u, cy + 12 * u], radius=u, fill=(255, 255, 255, 255))

def arrow(d, x0, x1, y, u):
    d.line([(x0, y), (x1, y)], fill=(255, 255, 255, 255), width=int(2.2 * u))
    s = 1 if x1 > x0 else -1
    d.polygon([(x1 + s * 3 * u, y), (x1 - s * 1 * u, y - 4 * u), (x1 - s * 1 * u, y + 4 * u)], fill=(255, 255, 255, 255))

def icon_ds(d, cx, cy, u):
    arrow(d, cx - 10 * u, cx + 7 * u, cy - 5 * u, u)
    arrow(d, cx + 10 * u, cx - 7 * u, cy + 5 * u, u)

def icon_mace(d, cx, cy, u):
    d.line([(cx - 9 * u, cy + 10 * u), (cx + 3 * u, cy - 2 * u)], fill=(255, 255, 255, 255), width=int(3 * u))
    d.ellipse([cx - 1 * u, cy - 12 * u, cx + 12 * u, cy + 1 * u], fill=(255, 255, 255, 255))
    for ang in [(0, -1), (1, 0), (0, 1), (-1, 0)]:
        px, py = cx + 5.5 * u + ang[0] * 8 * u, cy - 5.5 * u + ang[1] * 8 * u
        d.ellipse([px - 1.6 * u, py - 1.6 * u, px + 1.6 * u, py + 1.6 * u], fill=(255, 255, 255, 255))

def icon_br(d, cx, cy, u):
    d.ellipse([cx - 11 * u, cy - 11 * u, cx + 11 * u, cy + 11 * u], outline=(255, 255, 255, 255), width=int(2.2 * u))
    d.ellipse([cx - 5 * u, cy - 5 * u, cx + 5 * u, cy + 5 * u], outline=(255, 255, 255, 255), width=int(2.2 * u))
    d.ellipse([cx - 1.8 * u, cy - 1.8 * u, cx + 1.8 * u, cy + 1.8 * u], fill=(255, 255, 255, 255))

def banner(name, text, icon, path):
    w, h = 260 * K, 32 * K
    im = glass(w, h, 15 * K, (40, 80, 140, 238), (10, 24, 52, 244), (90, 175, 240, 255), int(1.6 * K))
    tile = glass(24 * K, 24 * K, 7 * K, (130, 225, 255, 255), (40, 120, 210, 255), (200, 240, 255, 255), int(1.2 * K))
    im.alpha_composite(tile, (5 * K, 4 * K))
    d = ImageDraw.Draw(im)
    icon(d, 17 * K, 16 * K, K * 0.78)
    f = ImageFont.truetype(FB, int(14 * K))
    d.text((38 * K, 16 * K), text, font=f, fill=(240, 248, 255, 255), anchor="lm")
    d.line([(38 * K, 28 * K), (210 * K, 28 * K)], fill=(90, 210, 255, 90), width=K)
    im.save(path)

banner("pof", "PILLAR OF FORTUNE", icon_pof, "panel_banner_pof.png")
banner("ds", "DEATHSWAP", icon_ds, "panel_banner_ds.png")
banner("mace", "MACE ROULETTE", icon_mace, "panel_banner_mace.png")
banner("br", "BATTLE ROYALE", icon_br, "panel_banner_br.png")

labels = ["SETTING", "PEMAIN", "LOBBY"]
fs = ImageFont.truetype(FB, int(7.4 * K))
for active in range(3):
    w, h = 300 * K, 16 * K
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pw = 96 * K
    for i in range(3):
        x0 = i * (pw + 6 * K)
        if i == active:
            pill = glass(pw, h, 8 * K, (125, 222, 255, 255), (50, 140, 220, 255), (200, 240, 255, 255), int(K))
            col = (8, 24, 52, 255)
        else:
            pill = glass(pw, h, 8 * K, (16, 34, 68, 235), (10, 22, 46, 240), (60, 110, 170, 255), int(K))
            col = (140, 175, 215, 255)
        im.alpha_composite(pill, (x0, 0))
        d = ImageDraw.Draw(im)
        d.text((x0 + pw // 2, h // 2), str(i + 1) + "  " + labels[i], font=fs, fill=col, anchor="mm")
    im.save("panel_step%d.png" % (active + 1))

providers = [{"type": "reference", "id": "minecraft:default"}]
for ch, f, hgt, asc in [(0xE100, "panel_banner_pof", 32, 19), (0xE101, "panel_banner_ds", 32, 19),
                        (0xE102, "panel_banner_mace", 32, 19), (0xE103, "panel_banner_br", 32, 19),
                        (0xE110, "panel_step1", 16, 12), (0xE111, "panel_step2", 16, 12), (0xE112, "panel_step3", 16, 12)]:
    providers.append({"type": "bitmap", "file": "zetminigames:font/%s.png" % f, "ascent": asc, "height": hgt, "chars": [chr(ch)]})
json.dump({"providers": providers}, open("panel.json", "w"), indent=2)
print("ok")
