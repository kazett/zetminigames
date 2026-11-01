#!/usr/bin/env python3
"""Generate semua PNG + hud.json resource pack POF. Jalanin: python3 gen_assets.py (butuh Pillow)."""
import json
from PIL import Image, ImageDraw

GOLD, GOLD_D, GOLD_L = (255, 214, 60, 255), (170, 120, 20, 255), (255, 240, 160, 255)
EDGE, EDGE_HI = (78, 90, 140, 255), (255, 212, 70, 255)

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))

def panel(w, h, top, bot, edge, hl=None, round_=2):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rectangle([0, 0, w - 1, h - 1], fill=edge)
    for y in range(1, h - 1):
        d.line([(1, y), (w - 2, y)], fill=lerp(top, bot, (y - 1) / max(1, h - 3)))
    if hl: d.line([(2, 1), (w - 3, 1)], fill=hl)
    for r in range(round_):                       # sudut membulat
        for x, y in [(r, round_ - 1 - r)]:
            pass
    for (cx, cy, sx, sy) in [(0, 0, 1, 1), (w - 1, 0, -1, 1), (0, h - 1, 1, -1), (w - 1, h - 1, -1, -1)]:
        for i in range(round_):
            for j in range(round_ - i):
                im.putpixel((cx + sx * i, cy + sy * j), (0, 0, 0, 0))
    return im

def ring(w, h, colors):
    """Frame berlubang (interior transparan) dengan ring berlapis."""
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    for i, c in enumerate(colors):
        d.rectangle([i, i, w - 1 - i, h - 1 - i], outline=c)
    for x, y in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        im.putpixel((x, y), (0, 0, 0, 0))
    return im

def sprite(rows, pal):
    im = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c in pal: im.putpixel((x, y), pal[c])
    return im

# ================= HUD =================
p = panel(150, 42, (28, 32, 58, 245), (12, 14, 28, 245), (74, 86, 136, 255), hl=(52, 60, 102, 255))
d = ImageDraw.Draw(p)
d.line([(5, 12), (144, 12)], fill=(42, 48, 84, 255))           # garis pemisah kepala / info
d.line([(5, 32), (144, 32)], fill=(42, 48, 84, 255))           # garis pemisah bar / border
d.line([(10, 0), (139, 0)], fill=(255, 214, 60, 255))          # aksen emas tipis di atas
p.save("hud_panel.png")
ring(10, 10, [(96, 108, 158, 255)]).save("hud_frame.png")

skull = sprite(["........", ".XXXXXX.", "XXXXXXXX", "XooXXooX", "XooXXooX", "XXXooXXX", ".XXXXXX.", "..X.X.X."],
               {"X": (236, 238, 248, 255), "o": (20, 22, 36, 255)})
bgs = Image.new("RGBA", (8, 8), (30, 30, 46, 255)); bgs.alpha_composite(skull); bgs.save("hud_skull.png")
sprite(["...GG...", "..GGGG.G", ".GGGGGG.", "G.GGGG..", "..GGG...", "..BBBB..", "..BBBB..", "...BB..."],
       {"G": (96, 214, 108, 255), "B": (184, 116, 62, 255)}).save("hud_item.png")
sprite(["X......X", ".XX..XX.", "..XXXX..", "...XX...", "...XX...", "..XXXX..", ".XX..XX.", "X......X"],
       {"X": (230, 234, 246, 255)}).save("hud_alive.png")
sprite(["........", "X..XX..X", "XX.XX.XX", "XXXXXXXX", "XXXXXXXX", ".XXXXXX.", "........", "........"],
       {"X": GOLD}).save("hud_logo.png")
sprite(["XX....XX", "X......X", "........", "...XX...", "...XX...", "........", "X......X", "XX....XX"],
       {"X": (255, 112, 112, 255)}).save("hud_border.png")

bar = Image.new("RGBA", (110, 5), (0, 0, 0, 0)); d = ImageDraw.Draw(bar)
for k in range(11):
    x0 = k * 10
    d.rectangle([x0, 0, x0 + 9, 4], fill=(8, 10, 20, 255))
    d.rectangle([x0, 1, x0 + 9, 3], fill=(40, 44, 72, 255))
    if k > 0:
        d.rectangle([x0, 1, x0 + k - 1, 3], fill=(255, 196, 40, 255))
        d.line([(x0, 1), (x0 + k - 1, 1)], fill=(255, 238, 150, 255))
bar.save("hud_bar.png")
Image.new("RGBA", (182, 5), (0, 0, 0, 0)).save("bar_clear.png")

# ikon tombol dialog: trophy, users, flag, check, cross, play, back (8x8 per ikon)
ICONS = [
    (["XXXXXXXX", "XXXXXXXX", "X.XXXX.X", ".XXXXXX.", "..XXXX..", "...XX...", "...XX...", "..XXXX.."], GOLD),
    ([".XX..XX.", ".XX..XX.", "........", "XXX..XXX", "XXX..XXX", "XXX..XXX", "........", "........"], (120, 190, 255, 255)),
    (["XXXXXX..", "XXXXXX..", "XXXXX...", "XXXXXX..", "X.......", "X.......", "X.......", "X......."], (255, 255, 255, 255)),
    (["........", "......XX", ".....XX.", "X...XX..", "XX.XX...", ".XXX....", "..X.....", "........"], (100, 230, 120, 255)),
    (["XX....XX", ".XX..XX.", "..XXXX..", "...XX...", "..XXXX..", ".XX..XX.", "XX....XX", "........"], (240, 80, 80, 255)),
    ([".X......", ".XX.....", ".XXX....", ".XXXX...", ".XXX....", ".XX.....", ".X......", "........"], (100, 230, 120, 255)),
    (["...X....", "..XX....", ".XXXXXXX", "XXXXXXXX", ".XXXXXXX", "..XX....", "...X....", "........"], (200, 206, 224, 255)),
]
sheet = Image.new("RGBA", (8 * len(ICONS), 8), (0, 0, 0, 0))
for i, (rows, col) in enumerate(ICONS):
    sheet.alpha_composite(sprite(rows, {"X": col}), (i * 8, 0))
sheet.save("hud_icons.png")

# ---- layar WINNER ----
F = {"W": ["X...X", "X...X", "X...X", "X.X.X", "X.X.X", "XX.XX", "X...X"],
     "I": ["XXXXX", "..X..", "..X..", "..X..", "..X..", "..X..", "XXXXX"],
     "N": ["X...X", "XX..X", "XX..X", "X.X.X", "X..XX", "X..XX", "X...X"],
     "E": ["XXXXX", "X....", "X....", "XXXX.", "X....", "X....", "XXXXX"],
     "R": ["XXXX.", "X...X", "X...X", "XXXX.", "X.X..", "X..X.", "X...X"]}
def make_banner(text, fname, top, bot, tail, edge, hi, ink, size):
    ban = Image.new("RGBA", (96, 28), (0, 0, 0, 0)); d = ImageDraw.Draw(ban)
    for side in (0, 1):                                              # ekor pita (swallowtail)
        x0, x1 = (0, 12) if side == 0 else (83, 95)
        d.rectangle([x0, 4, x1, 19], fill=tail, outline=edge)
        for y in range(4, 20):
            w = 6 - abs(y - 11)
            for i in range(max(0, w)):
                ban.putpixel((x0 + i if side == 0 else x1 - i, y), (0, 0, 0, 0))
    d.rectangle([8, 0, 87, 15], fill=edge)
    for y in range(1, 15):
        d.line([(9, y), (86, y)], fill=lerp(top, bot, (y - 1) / 13))
    d.line([(9, 1), (86, 1)], fill=hi)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf", size)
    except Exception:
        font = None
    if font:
        tmp = Image.new("L", (80, 16), 0); td = ImageDraw.Draw(tmp); td.fontmode = "1"
        tw = td.textlength(text, font=font)
        td.text((int(40 - tw / 2 + 0.5), (16 - size) // 2 - 1), text, font=font, fill=255)
        for yy in range(16):
            for xx in range(80):
                if tmp.getpixel((xx, yy)):
                    ban.putpixel((8 + xx, yy), ink)
    else:                                                            # font cadangan (hanya huruf WINER)
        x = 48 - len(text) * 6
        for ch in text:
            for r, row in enumerate(F.get(ch, [])):
                for c, v in enumerate(row):
                    if v == "X":
                        d.rectangle([x + c * 2, 1 + r * 2, x + c * 2 + 1, 1 + r * 2 + 1], fill=ink)
            x += 12
    ban.save(fname)

from PIL import ImageFont
make_banner("WINNER", "hud_banner.png", (255, 232, 120, 255), (238, 168, 28, 255), (176, 118, 22, 255),
            (70, 44, 8, 255), GOLD_L, (58, 32, 6, 255), 15)
make_banner("ROUND WIN", "hud_banner_round.png", (226, 236, 255, 255), (146, 168, 216, 255), (92, 112, 164, 255),
            (28, 38, 72, 255), (246, 250, 255, 255), (18, 26, 54, 255), 12)
make_banner("SERI", "hud_banner_draw.png", (236, 208, 255, 255), (168, 118, 224, 255), (112, 72, 164, 255),
            (42, 22, 72, 255), (250, 232, 255, 255), (34, 14, 62, 255), 15)
ring(12, 12, [GOLD, GOLD_D]).save("hud_winframe.png")

DIGITS = {
    1: ["..X..", ".XX..", "..X..", "..X..", "..X..", "..X..", ".XXX."],
    2: [".XXX.", "X...X", "....X", "...X.", "..X..", ".X...", "XXXXX"],
    3: ["XXXX.", "....X", "....X", ".XXX.", "....X", "....X", "XXXX."],
    4: ["...X.", "..XX.", ".X.X.", "X..X.", "XXXXX", "...X.", "...X."],
    5: ["XXXXX", "X....", "XXXX.", "....X", "....X", "X...X", ".XXX."],
}

def draw_digit(im, n, x0, y0, top, bot, outline):
    rows = DIGITS[n]
    cells = [(x, y) for y, r in enumerate(rows) for x, c in enumerate(r) if c == "X"]
    sc = 3
    for (x, y) in cells:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                ImageDraw.Draw(im).rectangle([x0 + x * sc + dx, y0 + y * sc + dy, x0 + x * sc + sc - 1 + dx, y0 + y * sc + sc - 1 + dy], fill=outline)
    for (x, y) in cells:
        for yy in range(sc):
            t = (y * sc + yy) / (len(rows) * sc - 1)
            ImageDraw.Draw(im).line([(x0 + x * sc, y0 + y * sc + yy), (x0 + x * sc + sc - 1, y0 + y * sc + yy)], fill=lerp(top, bot, t))

def badge(w, h, edge, inner_edge, bg_top, bg_bot):
    im = panel(w, h, bg_top, bg_bot, edge, hl=None, round_=3)
    d = ImageDraw.Draw(im)
    d.rectangle([2, 2, w - 3, h - 3], outline=inner_edge)
    d.line([(4, 3), (w - 5, 3)], fill=lerp(inner_edge, (255, 255, 255, 255), 0.35))
    return im

def swap_arrows(im, x, y, col):
    d = ImageDraw.Draw(im)
    d.rectangle([x, y + 3, x + 8, y + 4], fill=col)
    d.polygon([(x + 7, y), (x + 11, y + 4), (x + 7, y + 8)], fill=col)
    d.rectangle([x + 3, y + 12, x + 11, y + 13], fill=col)
    d.polygon([(x + 4, y + 9), (x, y + 13), (x + 4, y + 17)], fill=col)

count = Image.new("RGBA", (28 * 5, 28), (0, 0, 0, 0))
warn = Image.new("RGBA", (44 * 5, 28), (0, 0, 0, 0))
for n in range(1, 6):
    b = badge(28, 28, (60, 40, 6, 255), (255, 200, 50, 255), (46, 40, 70, 255), (18, 16, 36, 255))
    draw_digit(b, n, 6, 3, (255, 250, 190, 255), (255, 190, 40, 255), (58, 34, 4, 255))
    count.paste(b, ((n - 1) * 28, 0))
    w = badge(44, 28, (70, 10, 14, 255), (255, 92, 92, 255), (64, 26, 44, 255), (30, 12, 28, 255))
    swap_arrows(w, 6, 5, (255, 190, 190, 255))
    draw_digit(w, n, 26, 3, (255, 214, 214, 255), (255, 80, 80, 255), (74, 8, 12, 255))
    warn.paste(w, ((n - 1) * 44, 0))
count.save("hud_count.png")
warn.save("hud_tpwarn.png")

make_banner("MULAI!", "hud_banner_go.png", (190, 255, 170, 255), (72, 190, 88, 255), (44, 128, 58, 255),
            (14, 52, 22, 255), (230, 255, 220, 255), (10, 44, 16, 255), 15)
make_banner("TUKAR POSISI", "hud_banner_swap.png", (255, 196, 236, 255), (206, 96, 190, 255), (134, 56, 122, 255),
            (60, 18, 56, 255), (255, 226, 248, 255), (50, 10, 46, 255), 10)
make_banner("TERELIMINASI", "hud_banner_elim.png", (255, 176, 176, 255), (214, 70, 70, 255), (140, 40, 40, 255),
            (62, 12, 12, 255), (255, 214, 214, 255), (52, 8, 8, 255), 10)

# ================= WIDGET dialog =================
def btn(top, bot, edge, hl):  return panel(200, 20, top, bot, edge, hl=hl, round_=2)
btn((52, 60, 98, 255), (30, 34, 60, 255), (92, 104, 156, 255), (70, 80, 124, 255)).save("ui_button.png")
btn((84, 94, 148, 255), (46, 52, 90, 255), EDGE_HI, (120, 132, 190, 255)).save("ui_button_hl.png")
btn((34, 36, 50, 255), (26, 28, 40, 255), (58, 60, 78, 255), None).save("ui_button_off.png")
panel(200, 20, (8, 10, 20, 255), (14, 16, 30, 255), EDGE, round_=1).save("ui_field.png")
panel(200, 20, (8, 10, 20, 255), (14, 16, 30, 255), EDGE_HI, round_=1).save("ui_field_hl.png")

def check(sel, hl):
    im = panel(17, 17, (8, 10, 20, 255), (16, 18, 34, 255), EDGE_HI if hl else EDGE, round_=1)
    if sel:
        dd = ImageDraw.Draw(im)
        for x, y in [(4, 8), (5, 9), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5)]:
            dd.rectangle([x, y, x + 1, y + 1], fill=GOLD)
    return im
check(0, 0).save("ui_check.png"); check(0, 1).save("ui_check_hl.png")
check(1, 0).save("ui_check_on.png"); check(1, 1).save("ui_check_on_hl.png")

# ================= hud.json =================
def bm(f, asc, hei, chars): return {"type": "bitmap", "file": f"zetminigames:font/{f}.png", "ascent": asc, "height": hei, "chars": chars}
space = {"type": "space", "advances": {" ": 4}}
for k in range(10):
    space["advances"][chr(0xF800 + k)] = -(2 ** k); space["advances"][chr(0xF820 + k)] = 2 ** k
blank = "\u0000" * 16
ascii_rows = [blank, blank, " !\"#$%&'()*+,-./", "0123456789:;<=>?", "@ABCDEFGHIJKLMNO", "PQRSTUVWXYZ[\\]^_",
              "`abcdefghijklmno", "pqrstuvwxyz{|}~\u0000"] + [blank] * 8
prov = [space, {"type": "bitmap", "file": "minecraft:font/ascii.png", "ascent": -4, "height": 8, "chars": ascii_rows},
        bm("hud_panel", 10, 42, ["\ue000"]), bm("hud_skull", 8, 8, ["\ue001"]), bm("hud_item", -4, 8, ["\ue002"]),
        bm("hud_alive", -4, 8, ["\ue003"]), bm("hud_frame", 9, 10, ["\ue004"]), bm("hud_logo", 7, 8, ["\ue006"]),
        bm("hud_banner", 12, 28, ["\ue008"]), bm("hud_banner_round", 12, 28, ["\ue00b"]), bm("hud_banner_draw", 12, 28, ["\ue00c"]), bm("hud_winframe", 10, 12, ["\ue009"]),
        bm("hud_count", 12, 28, ["".join(chr(0xE030 + i) for i in range(5))]),
        bm("hud_tpwarn", 12, 28, ["".join(chr(0xE035 + i) for i in range(5))]),
        bm("hud_banner_go", 12, 28, ["\ue03a"]), bm("hud_banner_swap", 12, 28, ["\ue03b"]), bm("hud_banner_elim", 12, 28, ["\ue03c"]),
        bm("hud_bar", -16, 5, ["".join(chr(0xE010 + i) for i in range(11))]),
        bm("hud_icons", 7, 8, ["".join(chr(0xE020 + i) for i in range(len(ICONS)))])]
json.dump({"providers": prov}, open("hud.json", "w"), indent=2)
# font kedua: baris border (posisi lebih ke bawah)
low = [{"type": "space", "advances": {" ": 4}},
       {"type": "bitmap", "file": "minecraft:font/ascii.png", "ascent": -23, "height": 8, "chars": ascii_rows},
       bm("hud_border", -23, 8, ["\ue00a"])]
json.dump({"providers": low}, open("hud_low.json", "w"), indent=2)
print("ok")
