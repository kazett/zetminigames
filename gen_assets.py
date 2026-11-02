#!/usr/bin/env python3
"""Generate semua PNG + hud.json resource pack ZetMinigames.
Tema: modern blue glass (HD, bersih, rapi).
Jalanin: python3 gen_assets.py (butuh Pillow).
"""
import json
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# ===================== PALETTE: BLUE GLASS =====================
# Glass dark → light, cyan accent, soft white highlight
GLASS_TOP   = (36,  72, 128, 230)
GLASS_BOT   = (12,  28,  56, 235)
GLASS_EDGE  = (80, 160, 230, 255)
GLASS_HL    = (160, 220, 255, 200)   # top highlight
GLASS_IN    = (50, 110, 180, 255)    # inner edge

CYAN        = (90,  210, 255, 255)
CYAN_L      = (180, 240, 255, 255)
CYAN_D      = (40,  130, 200, 255)
WHITE       = (240, 248, 255, 255)
SOFT_W      = (200, 220, 240, 180)

GOLD        = (255, 214, 60, 255)    # tetap dipakai di banner winner
GOLD_D      = (170, 120, 20, 255)
GOLD_L      = (255, 240, 160, 255)

RED         = (255, 90,  90,  255)
GREEN       = (90,  230, 140, 255)
ORANGE      = (255, 160, 60,  255)

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))

def panel(w, h, top=GLASS_TOP, bot=GLASS_BOT, edge=GLASS_EDGE, hl=GLASS_HL, round_=3):
    """Panel glass dengan sudut membulat + highlight atas + soft inner edge."""
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # outer edge
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=round_, fill=edge)
    # body gradient
    for y in range(1, h - 1):
        t = (y - 1) / max(1, h - 3)
        col = lerp(top, bot, t)
        d.line([(1, y), (w - 2, y)], fill=col)
    # top glass highlight strip
    if hl:
        for y in range(1, min(4, h // 3)):
            a = hl[3] * (1 - y / 4)
            d.line([(2, y), (w - 3, y)], fill=(hl[0], hl[1], hl[2], int(a)))
    # soft inner edge
    d.rounded_rectangle([1, 1, w - 2, h - 2], radius=max(1, round_ - 1), outline=GLASS_IN)
    # clear rounded corners properly (extra pixels)
    for r in range(round_):
        for (cx, cy, sx, sy) in [(0, 0, 1, 1), (w - 1, 0, -1, 1), (0, h - 1, 1, -1), (w - 1, h - 1, -1, -1)]:
            for i in range(round_):
                for j in range(round_ - i):
                    if i + j >= round_ - 1:
                        im.putpixel((cx + sx * i, cy + sy * j), (0, 0, 0, 0))
    return im

def ring(w, h, colors):
    """Frame berlubang (interior transparan) dengan ring berlapis + soft corner."""
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for i, c in enumerate(colors):
        d.rectangle([i, i, w - 1 - i, h - 1 - i], outline=c)
    for x, y in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        im.putpixel((x, y), (0, 0, 0, 0))
    return im

def sprite(rows, pal):
    im = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c in pal:
                im.putpixel((x, y), pal[c])
    return im

# ================= HUD PANELS =================
# Panel utama 150x42 (4 baris)
p = panel(150, 42, round_=3)
d = ImageDraw.Draw(p)
# garis pemisah halus (glass divider)
div = (50, 100, 170, 160)
d.line([(6, 12), (143, 12)], fill=div)
d.line([(6, 32), (143, 32)], fill=div)
# aksen cyan tipis di atas
d.line([(8, 1), (141, 1)], fill=CYAN)
p.save("hud_panel.png")

# Panel BR 150x34 (3 baris)
pb = panel(150, 34, round_=3)
ImageDraw.Draw(pb).line([(6, 12), (143, 12)], fill=div)
ImageDraw.Draw(pb).line([(8, 1), (141, 1)], fill=CYAN)
pb.save("hud_panel_br.png")

# Frame kepala 10x10
# frame kepala — multi-layer cyan glass ring
ring(10, 10, [
    (30, 70, 130, 255),
    CYAN_D,
    CYAN,
    CYAN_L,
    (220, 245, 255, 220),
]).save("hud_frame.png")

# ================= ICONS 8x8 — blue glass modern =================
# skull
skull = sprite(
    ["........",
     ".XXXXXX.",
     "XXXXXXXX",
     "X..XX..X",
     "X..XX..X",
     "XXX..XXX",
     ".XX..XX.",
     "..X..X.."],
    {"X": (230, 240, 255, 255)})
bgs = Image.new("RGBA", (8, 8), (18, 32, 58, 255))
for yy in range(8):
    for xx in range(8):
        if bgs.getpixel((xx, yy))[3]:
            # subtle top glass highlight on plate
            if yy == 0:
                bgs.putpixel((xx, yy), (40, 70, 110, 255))
bgs.alpha_composite(skull)
bgs.save("hud_skull.png")

# item — package with cyan band (clean)
sprite(
    [".XXXXXX.",
     "XHHHHHHX",
     "XHCCCHHX",
     "CCCCCCCC",
     "XHCCCHHX",
     "XHHHHHHX",
     "XHHHHHHX",
     ".XXXXXX."],
    {"X": (50, 140, 90, 255), "H": (100, 220, 150, 255), "C": (80, 200, 255, 255)}).save("hud_item.png")

# alive — heart / life cyan (lebih jelas di 8x8)
sprite(
    [".XX..XX.",
     "XXXXXXXX",
     "XXXXXXXX",
     "XXXXXXXX",
     ".XXXXXX.",
     "..XXXX..",
     "...XX...",
     "........"],
    {"X": (90, 200, 255, 255)}).save("hud_alive.png")

# logo — diamond / gem
sprite(
    ["...XX...",
     "..XXXX..",
     ".XXXXXX.",
     "XXXXXXXX",
     ".XXXXXX.",
     "..XXXX..",
     "...XX...",
     "........"],
    {"X": CYAN}).save("hud_logo.png")

# border — cyan X (blue glass, bukan merah)
sprite(
    ["XX....XX",
     "XXX..XXX",
     ".XX..XX.",
     "..XXXX..",
     "..XXXX..",
     ".XX..XX.",
     "XXX..XXX",
     "XX....XX"],
    {"X": (100, 200, 255, 255)}).save("hud_border.png")

# Progress bar (11 state, 10px per chunk, height 5) — cyan glass fill
bar = Image.new("RGBA", (110, 5), (0, 0, 0, 0))
d = ImageDraw.Draw(bar)
for k in range(11):
    x0 = k * 10
    # dark track
    d.rectangle([x0, 0, x0 + 9, 4], fill=(6, 14, 28, 255))
    d.rectangle([x0, 1, x0 + 9, 3], fill=(24, 48, 88, 255))
    if k > 0:
        fill_end = x0 + k - 1
        d.rectangle([x0, 1, fill_end, 3], fill=CYAN)
        d.line([(x0, 1), (fill_end, 1)], fill=CYAN_L)          # top highlight
        d.line([(x0, 3), (fill_end, 3)], fill=CYAN_D)          # bottom shade
bar.save("hud_bar.png")
Image.new("RGBA", (182, 5), (0, 0, 0, 0)).save("bar_clear.png")

# Dialog icons (trophy, users, flag, check, cross, play, back) — cleaner shapes
ICONS = [
    ([".XXXXXX.", "XXXXXXXX", "X.XXXX.X", ".XXXXXX.", "..XXXX..", "...XX...", "...XX...", "..XXXX.."], CYAN),
    ([".XX..XX.", "XXXXXXXX", ".XX..XX.", "........", ".XXXXXX.", "XXXXXXXX", "XXXXXXXX", ".XXXXXX."], (140, 210, 255, 255)),
    (["XXXXXX..", "XXXXXXX.", "XXXXX...", "XXXXXX..", "X.......", "X.......", "X.......", "XXXX...."], WHITE),
    (["........", "......XX", ".....XX.", "X...XX..", "XX.XX...", ".XXX....", "..X.....", "........"], GREEN),
    (["XX....XX", "XXX..XXX", ".XX..XX.", "..XXXX..", "..XXXX..", ".XX..XX.", "XXX..XXX", "XX....XX"], RED),
    ([".X......", ".XX.....", ".XXX....", ".XXXX...", ".XXX....", ".XX.....", ".X......", "........"], GREEN),
    (["...X....", "..XX....", ".XXXXXXX", "XXXXXXXX", ".XXXXXXX", "..XX....", "...X....", "........"], (200, 225, 245, 255)),
]
sheet = Image.new("RGBA", (8 * len(ICONS), 8), (0, 0, 0, 0))
for i, (rows, col) in enumerate(ICONS):
    sheet.alpha_composite(sprite(rows, {"X": col}), (i * 8, 0))
sheet.save("hud_icons.png")

# BR icons (zone, pvp, kill, warn, clock) — sharper modern
BR_ICONS = [
    (["..XXXX..", ".XXXXXX.", "XX.XX.XX", "X.XXXX.X", "X.XXXX.X", "XX.XX.XX", ".XXXXXX.", "..XXXX.."], CYAN),
    (["XX....XX", "XXX..XXX", ".XX..XX.", "..XXXX..", "..XXXX..", ".XX..XX.", "XXX..XXX", "XX....XX"], RED),
    (["........", ".XXXXXX.", "XXXXXXXX", "XooXXooX", "XXXooXXX", ".XXXXXX.", "..X..X..", "........"], GOLD),
    (["...XX...", "..XXXX..", ".XXooXX.", "XXooooXX", "XXooooXX", "XXXXXXXX", "XXXooXXX", "XXXXXXXX"], ORANGE),
    (["..XXXX..", ".XXXXXX.", "XX.XX.XX", "X..XXX.X", "X...XX.X", "X......X", ".XXXXXX.", "..XXXX.."], (180, 225, 255, 255)),
]
brs = Image.new("RGBA", (8 * len(BR_ICONS), 8), (0, 0, 0, 0))
for i, (rows, col) in enumerate(BR_ICONS):
    brs.alpha_composite(sprite(rows, {"X": col, "o": (20, 24, 40, 255)}), (i * 8, 0))
brs.save("hud_br.png")

# Direction arrows (8 directions)
ARROW = ["...XX...", "..XXXX..", ".XXXXXX.", "XXXXXXXX", "...XX...", "...XX...", "...XX...", "...XX..."]
arrow_img = sprite(ARROW, {"X": WHITE})
arrows = Image.new("RGBA", (64, 8), (0, 0, 0, 0))
for i in range(8):
    big = arrow_img.resize((64, 64), Image.NEAREST).rotate(-45 * i, resample=Image.BICUBIC)
    small = big.resize((8, 8), Image.BOX)
    for yy in range(8):
        for xx in range(8):
            a = small.getpixel((xx, yy))[3]
            small.putpixel((xx, yy), (255, 255, 255, 255) if a >= 110 else (0, 0, 0, 0))
    arrows.alpha_composite(small, (i * 8, 0))
arrows.save("hud_arrows.png")

# ================= BANNERS (modern glass ribbon) =================
F = {
    "W": ["X...X", "X...X", "X...X", "X.X.X", "X.X.X", "XX.XX", "X...X"],
    "I": ["XXXXX", "..X..", "..X..", "..X..", "..X..", "..X..", "XXXXX"],
    "N": ["X...X", "XX..X", "XX..X", "X.X.X", "X..XX", "X..XX", "X...X"],
    "E": ["XXXXX", "X....", "X....", "XXXX.", "X....", "X....", "XXXXX"],
    "R": ["XXXX.", "X...X", "X...X", "XXXX.", "X.X..", "X..X.", "X...X"],
}

def make_banner(text, fname, top, bot, tail, edge, hi, ink, size):
    ban = Image.new("RGBA", (96, 28), (0, 0, 0, 0))
    d = ImageDraw.Draw(ban)
    # swallowtail sides
    for side in (0, 1):
        x0, x1 = (0, 12) if side == 0 else (83, 95)
        d.rectangle([x0, 4, x1, 19], fill=tail, outline=edge)
        for y in range(4, 20):
            w = 6 - abs(y - 11)
            for i in range(max(0, w)):
                ban.putpixel((x0 + i if side == 0 else x1 - i, y), (0, 0, 0, 0))
    # main body
    d.rectangle([8, 0, 87, 15], fill=edge)
    for y in range(1, 15):
        d.line([(9, y), (86, y)], fill=lerp(top, bot, (y - 1) / 13))
    d.line([(9, 1), (86, 1)], fill=hi)
    # text
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf", size)
    except Exception:
        font = None
    if font:
        tmp = Image.new("L", (80, 16), 0)
        td = ImageDraw.Draw(tmp)
        td.fontmode = "1"
        tw = td.textlength(text, font=font)
        td.text((int(40 - tw / 2 + 0.5), (16 - size) // 2 - 1), text, font=font, fill=255)
        for yy in range(16):
            for xx in range(80):
                if tmp.getpixel((xx, yy)):
                    ban.putpixel((8 + xx, yy), ink)
    else:
        x = 48 - len(text) * 6
        for ch in text:
            for r, row in enumerate(F.get(ch, [])):
                for c, v in enumerate(row):
                    if v == "X":
                        d.rectangle([x + c * 2, 1 + r * 2, x + c * 2 + 1, 1 + r * 2 + 1], fill=ink)
            x += 12
    ban.save(fname)

make_banner("WINNER", "hud_banner.png",
            (255, 232, 120, 255), (238, 168, 28, 255), (176, 118, 22, 255),
            (70, 44, 8, 255), GOLD_L, (58, 32, 6, 255), 15)
make_banner("ROUND WIN", "hud_banner_round.png",
            (200, 230, 255, 255), (80, 140, 210, 255), (50, 90, 160, 255),
            (20, 40, 80, 255), (230, 245, 255, 255), (12, 24, 50, 255), 12)
make_banner("DRAW", "hud_banner_draw.png",
            (220, 200, 255, 255), (140, 100, 210, 255), (90, 60, 150, 255),
            (40, 20, 70, 255), (245, 235, 255, 255), (30, 12, 50, 255), 15)
ring(12, 12, [CYAN, CYAN_D]).save("hud_winframe.png")

# ================= COUNTDOWN BADGES (blue glass) =================
DIGITS = {
    1: ["..X..", ".XX..", "..X..", "..X..", "..X..", "..X..", ".XXX."],
    2: [".XXX.", "X...X", "....X", "...X.", "..X..", ".X...", "XXXXX"],
    3: ["XXXX.", "....X", "....X", ".XXX.", "....X", "....X", "XXXX."],
    4: ["...X.", "..XX.", ".X.X.", "X..X.", "XXXXX", "...X.", "...X."],
    5: ["XXXXX", "X....", "XXXX.", "....X", "....X", "X...X", ".XXX."],
}

def draw_digit(im, n, x0, y0, top, bot, outline):
    """Digit 5x7 scaled 3x, soft outline + vertical gradient fill."""
    rows = DIGITS[n]
    cells = [(x, y) for y, r in enumerate(rows) for x, c in enumerate(r) if c == "X"]
    sc = 3
    d = ImageDraw.Draw(im)
    # outline
    for (x, y) in cells:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                d.rectangle(
                    [x0 + x * sc + dx, y0 + y * sc + dy,
                     x0 + x * sc + sc - 1 + dx, y0 + y * sc + sc - 1 + dy],
                    fill=outline)
    # fill gradient
    for (x, y) in cells:
        for yy in range(sc):
            t = (y * sc + yy) / max(1, len(rows) * sc - 1)
            d.line(
                [(x0 + x * sc, y0 + y * sc + yy),
                 (x0 + x * sc + sc - 1, y0 + y * sc + yy)],
                fill=lerp(top, bot, t))
    # top micro-highlight on each cell
    for (x, y) in cells:
        d.line(
            [(x0 + x * sc, y0 + y * sc),
             (x0 + x * sc + sc - 1, y0 + y * sc)],
            fill=lerp(top, (255, 255, 255, 255), 0.35))

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
    # countdown badge — full blue glass
    b = badge(28, 28, (50, 120, 200, 255), CYAN_L, (32, 65, 120, 255), (12, 28, 55, 255))
    ImageDraw.Draw(b).rectangle([3, 3, 24, 24], outline=CYAN)
    draw_digit(b, n, 6, 3, (230, 248, 255, 255), CYAN, (25, 55, 100, 255))
    count.paste(b, ((n - 1) * 28, 0))
    # tpwarn / swap badge — SAME blue glass theme (bukan merah)
    w = badge(44, 28, (50, 120, 200, 255), CYAN_L, (32, 65, 120, 255), (12, 28, 55, 255))
    ImageDraw.Draw(w).rectangle([3, 3, 40, 24], outline=CYAN)
    swap_arrows(w, 5, 5, CYAN_L)
    draw_digit(w, n, 26, 3, (230, 248, 255, 255), CYAN, (25, 55, 100, 255))
    warn.paste(w, ((n - 1) * 44, 0))
count.save("hud_count.png")
warn.save("hud_tpwarn.png")

make_banner("GO!", "hud_banner_go.png",
            (170, 255, 180, 255), (50, 180, 90, 255), (30, 120, 60, 255),
            (10, 50, 25, 255), (220, 255, 230, 255), (8, 40, 18, 255), 15)
make_banner("READY", "hud_banner_ready.png",
            (180, 230, 255, 255), (60, 140, 220, 255), (30, 90, 160, 255),
            (10, 30, 70, 255), (220, 240, 255, 255), (6, 24, 55, 255), 13)
make_banner("SWAP", "hud_banner_swap.png",
            (255, 190, 230, 255), (190, 80, 180, 255), (120, 40, 110, 255),
            (50, 15, 50, 255), (255, 220, 245, 255), (40, 8, 40, 255), 13)
make_banner("ELIMINATED", "hud_banner_elim.png",
            (255, 170, 170, 255), (200, 60, 60, 255), (130, 30, 30, 255),
            (55, 10, 10, 255), (255, 210, 210, 255), (45, 6, 6, 255), 11)

# ================= UI WIDGETS (dialog) =================
def btn(top, bot, edge, hl):
    return panel(200, 20, top, bot, edge, hl=hl, round_=3)

btn((40, 70, 120, 255), (20, 40, 80, 255), GLASS_EDGE, (100, 160, 220, 255)).save("ui_button.png")
btn((70, 120, 190, 255), (40, 80, 140, 255), CYAN_L, (140, 200, 255, 255)).save("ui_button_hl.png")
btn((30, 35, 50, 255), (20, 25, 40, 255), (60, 70, 90, 255), None).save("ui_button_off.png")

panel(200, 20, (10, 18, 36, 255), (16, 28, 50, 255), GLASS_EDGE, round_=2).save("ui_field.png")
panel(200, 20, (10, 18, 36, 255), (16, 28, 50, 255), CYAN, round_=2).save("ui_field_hl.png")

def check(sel, hl):
    im = panel(17, 17, (10, 18, 36, 255), (18, 30, 55, 255),
               CYAN if hl else GLASS_EDGE, round_=2)
    if sel:
        dd = ImageDraw.Draw(im)
        for x, y in [(4, 8), (5, 9), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5)]:
            dd.rectangle([x, y, x + 1, y + 1], fill=CYAN)
    return im

check(0, 0).save("ui_check.png")
check(0, 1).save("ui_check_hl.png")
check(1, 0).save("ui_check_on.png")
check(1, 1).save("ui_check_on_hl.png")

# ================= HUD BOTTOM (actionbar) =================
panel(120, 13, GLASS_TOP, GLASS_BOT, GLASS_EDGE, hl=GLASS_HL, round_=2).save("hud_bottom.png")
sprite(
    ["...Y....",
     "..XYX...",
     ".XXYXX..",
     "XXXYXXX.",
     "XXYYYYXX",
     "XXYYYYXX",
     ".XXYYXX.",
     "..XXXX.."],
    {"X": (255, 100, 40, 255), "Y": (255, 220, 70, 255)}).save("hud_lava.png")

# ================= FONT JSON =================
def bm(f, asc, hei, chars):
    return {"type": "bitmap", "file": f"zetminigames:font/{f}.png", "ascent": asc, "height": hei, "chars": chars}

space = {"type": "space", "advances": {" ": 4}}
for k in range(10):
    space["advances"][chr(0xF800 + k)] = -(2 ** k)
    space["advances"][chr(0xF820 + k)] = 2 ** k

blank = "\u0000" * 16
ascii_rows = [blank, blank, " !\"#$%&'()*+,-./", "0123456789:;<=>?",
              "@ABCDEFGHIJKLMNO", "PQRSTUVWXYZ[\\]^_",
              "`abcdefghijklmno", "pqrstuvwxyz{|}~\u0000"] + [blank] * 8

prov = [space,
        {"type": "bitmap", "file": "minecraft:font/ascii.png", "ascent": -4, "height": 8, "chars": ascii_rows},
        bm("hud_panel", 10, 42, ["\ue000"]),
        bm("hud_panel_br", 10, 34, ["\ue00d"]),
        bm("hud_skull", 8, 8, ["\ue001"]),
        bm("hud_item", -4, 8, ["\ue002"]),
        bm("hud_alive", -4, 8, ["\ue003"]),
        bm("hud_frame", 9, 10, ["\ue004"]),
        bm("hud_logo", 7, 8, ["\ue006"]),
        bm("hud_banner", 12, 28, ["\ue008"]),
        bm("hud_banner_round", 12, 28, ["\ue00b"]),
        bm("hud_banner_draw", 12, 28, ["\ue00c"]),
        bm("hud_winframe", 10, 12, ["\ue009"]),
        bm("hud_count", 12, 28, ["".join(chr(0xE030 + i) for i in range(5))]),
        bm("hud_tpwarn", 12, 28, ["".join(chr(0xE035 + i) for i in range(5))]),
        bm("hud_banner_go", 12, 28, ["\ue03a"]),
        bm("hud_banner_swap", 12, 28, ["\ue03b"]),
        bm("hud_banner_elim", 12, 28, ["\ue03c"]),
        bm("hud_banner_ready", 12, 28, ["\ue03d"]),
        bm("hud_bar", -16, 5, ["".join(chr(0xE010 + i) for i in range(11))]),
        bm("hud_icons", 7, 8, ["".join(chr(0xE020 + i) for i in range(len(ICONS)))]),
        bm("hud_br", -4, 8, ["".join(chr(0xE040 + i) for i in range(len(BR_ICONS)))])]
json.dump({"providers": prov}, open("hud.json", "w"), indent=2)

low = [{"type": "space", "advances": {" ": 4}},
       {"type": "bitmap", "file": "minecraft:font/ascii.png", "ascent": -23, "height": 8, "chars": ascii_rows},
       bm("hud_border", -23, 8, ["\ue00a"]),
       bm("hud_br", -23, 8, ["".join(chr(0xE040 + i) for i in range(len(BR_ICONS)))]),
       bm("hud_arrows", -23, 8, ["".join(chr(0xE050 + i) for i in range(8))])]
json.dump({"providers": low}, open("hud_low.json", "w"), indent=2)

top = [{"type": "space", "advances": {" ": 4}},
       {"type": "bitmap", "file": "minecraft:font/ascii.png", "ascent": 8, "height": 8, "chars": ascii_rows},
       bm("hud_br", 8, 8, ["".join(chr(0xE040 + i) for i in range(len(BR_ICONS)))])]
json.dump({"providers": top}, open("hud_top.json", "w"), indent=2)

bottom = [space,
          {"type": "bitmap", "file": "minecraft:font/ascii.png", "ascent": 7, "height": 8, "chars": ascii_rows},
          bm("hud_bottom", 10, 13, ["\ue060"]),
          bm("hud_lava", 7, 8, ["\ue061"]),
          bm("hud_arrows", 7, 8, ["".join(chr(0xE050 + i) for i in range(8))]),
          bm("hud_br", 7, 8, ["".join(chr(0xE040 + i) for i in range(len(BR_ICONS)))])]
json.dump({"providers": bottom}, open("hud_bottom.json", "w"), indent=2)

print("ok - blue glass HD assets generated")
