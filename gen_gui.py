#!/usr/bin/env python3
"""Generate aset GUI custom (Pillar of Fortune, tema blue glass) buat resource pack ZetMinigames.

Cara kerja GUI:
  Plugin buka inventory chest 6 baris tanpa item. Gambar GUI = glyph raksasa di JUDUL inventory
  (font zetminigames:gui). Slot kosong cuma jadi area klik (hitbox) di atas gambar.

Output (semua ke folder ini, commit ke repo):
  Glyph font maksimal 256x256 px, jadi kanvas 352x444 dipotong jadi tile 176x222 (2 kolom x 2 baris).
  gui_layers.png    tile baris ATAS: [0,1]=dasar kiri/kanan, [2..7]=speed terpilih x3, [8..13]=toggle ON x3
  gui_layers_b.png  tile baris BAWAH: [0,1]=dasar kiri/kanan
  gui_text.png    atlas font angka/huruf buat nilai dinamis (60s, 5m, RANDOM, ...)
  gui.json / gui_b.json  font tile atas / bawah;  gui_v1.json / gui_v3.json  font nilai (baris 1 / baris 3)
  gui_slot_front.png / gui_slot_back.png  hover slot (cyan tipis)
  gui_preview.png (cuma buat dilihat, gak masuk pack)

Jalanin: python3 gen_gui.py   (butuh Pillow + font Inter)
Geometri (satuan = pixel GUI Minecraft, kanvas 176x222 = ukuran chest 6 baris):
  slot (r,c): x = 8 + 18c, y = 18 + 18r, ukuran 16x16
"""
import json
from PIL import Image, ImageDraw, ImageFont, ImageChops, ImageFilter

K = 4                    # supersample: 4 px gambar per 1 px GUI (hasil akhir 2 px per 1 px GUI)
OUT = 2                  # px texture per px GUI
W, H = 176, 222
INTER_B = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
INTER_X = "/usr/share/fonts/opentype/inter/Inter-ExtraBold.otf"
CAP = 0.727              # tinggi huruf kapital Inter / em

# ---------- palette blue glass ----------
BG_TOP, BG_MID, BG_BOT = (7, 20, 46), (13, 38, 80), (6, 16, 38)
CARD_TOP, CARD_BOT, CARD_EDGE = (36, 84, 152), (12, 34, 74), (88, 172, 247)
INPUT, INPUT_EDGE = (6, 16, 38), (66, 152, 238)
CYAN, CYAN_L = (118, 214, 255), (190, 240, 255)
TXT, DIM = (228, 240, 255), (140, 182, 232)
ACT_TOP, ACT_BOT, ACT_TXT = (128, 224, 255), (44, 142, 230), (5, 28, 70)
RED = (255, 130, 152)
OFF_TRACK, OFF_KNOB = (44, 78, 128), (140, 172, 214)


def row_y(r):
    return 18 + 18 * r


def new():
    return Image.new("RGBA", (W * K, H * K), (0, 0, 0, 0))


def bx(x0, y0, x1, y1):
    return [round(x0 * K), round(y0 * K), round(x1 * K) - 1, round(y1 * K) - 1]


def mask_rr(x0, y0, x1, y1, r):
    m = Image.new("L", (W * K, H * K), 0)
    ImageDraw.Draw(m).rounded_rectangle(bx(x0, y0, x1, y1), radius=max(0, r * K), fill=255)
    return m


def over(im, src, mask):
    """Tempel src (RGBA full-size atau warna) lewat mask 'L' dengan alpha-composite (tanpa pinggir gelap)."""
    if isinstance(src, tuple):
        src = Image.new("RGBA", im.size, src if len(src) == 4 else src + (255,))
    else:
        src = src.copy()
    src.putalpha(ImageChops.multiply(src.getchannel("A"), mask))
    im.alpha_composite(src)


def vgrad(y0, y1, top, bot, alpha=255):
    g = Image.new("RGBA", (W * K, H * K), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    a, b = round(y0 * K), round(y1 * K)
    for y in range(a, b):
        t = (y - a) / max(1, b - a - 1)
        c = tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)) + (alpha,)
        d.line([(0, y), (W * K, y)], fill=c)
    return g


def rrect(im, x0, y0, x1, y1, r, top=None, bot=None, fill=None, outline=None, ow=0.7, alpha=255):
    m1 = mask_rr(x0, y0, x1, y1, r)
    if top is not None:
        over(im, vgrad(y0, y1, top, bot, alpha), m1)
    elif fill is not None:
        over(im, tuple(fill[:3]) + (alpha,), m1)
    if outline is not None:
        m2 = mask_rr(x0 + ow, y0 + ow, x1 - ow, y1 - ow, r - ow)
        over(im, tuple(outline[:3]) + (255,), ImageChops.subtract(m1, m2))


def glow(im, x0, y0, x1, y1, r, color, strength=90, blur=3):
    m = mask_rr(x0, y0, x1, y1, r).filter(ImageFilter.GaussianBlur(blur * K))
    m = m.point(lambda v: int(v * strength / 255))
    over(im, tuple(color) + (255,), m)


def font(path, cap_gui):
    return ImageFont.truetype(path, max(4, round(cap_gui * K / CAP)))


def tw(s, cap, path=INTER_B, track=0.0):
    f = font(path, cap)
    return f.getlength(s) / K + track * max(0, len(s) - 1)


def text(im, s, x, base, cap, color, anchor="l", path=INTER_B, track=0.0):
    """x = kiri/tengah/kanan (anchor l/m/r), base = garis dasar huruf (px GUI)."""
    f = font(path, cap)
    w = tw(s, cap, path, track)
    if anchor == "m":
        x -= w / 2
    elif anchor == "r":
        x -= w
    d = ImageDraw.Draw(im)
    if track == 0:
        d.text((x * K, base * K), s, font=f, fill=color + (255,), anchor="ls")
    else:
        cx = x * K
        for ch in s:
            d.text((cx, base * K), ch, font=f, fill=color + (255,), anchor="ls")
            cx += f.getlength(ch) + track * K


def tri(im, cx, cy, direction, color=CYAN, s=2.4):
    d = ImageDraw.Draw(im)
    if direction < 0:
        pts = [(cx + s * 0.7, cy - s * 1.3), (cx + s * 0.7, cy + s * 1.3), (cx - s * 0.9, cy)]
    else:
        pts = [(cx - s * 0.7, cy - s * 1.3), (cx - s * 0.7, cy + s * 1.3), (cx + s * 0.9, cy)]
    d.polygon([(px * K, py * K) for px, py in pts], fill=color + (255,))


# ================= GEOMETRI KONTROL =================
TABS = [(8, 60), (62, 114), (116, 168)]
CARD_L, CARD_R = (8, 78), (98, 168)        # baris 1: item | border
RONDE, MAP = (8, 78), (80, 168)            # baris 3: ronde | map
SPEED_BOX = (62, 168)
SPEED_SEG = [(63, 96), (98, 132), (134, 167)]
SPEED_NAMES = ["PELAN", "SEDANG", "CEPAT"]
CHIPS = [(8, 60), (62, 132), (134, 168)]
CHIP_TXT = [("BOLEH", "SERI"), ("STOP KALAU", "PASTI MENANG"), ("MODE", "TES")]
BATAL, LANJUT = (8, 60), (62, 168)


# ================= BAGIAN GAMBAR =================
def draw_background(im):
    im.alpha_composite(vgrad(0, H * 0.55, BG_TOP, BG_MID))
    im.alpha_composite(vgrad(H * 0.55, H, BG_MID, BG_BOT))
    # redaman cahaya biru di atas
    glow(im, 20, -30, 156, 40, 30, (40, 110, 210), strength=110, blur=14)


def draw_card(im):
    glow(im, 3, 3, 173, 127, 6, (70, 160, 255), strength=120, blur=2.2)
    rrect(im, 3, 3, 173, 127, 6, top=CARD_TOP, bot=CARD_BOT, outline=CARD_EDGE, ow=0.9)
    # highlight kaca tipis di atas
    hl = Image.new("RGBA", (W * K, H * K), (0, 0, 0, 0))
    ImageDraw.Draw(hl).rounded_rectangle(bx(5, 4.2, 171, 9), radius=3 * K, fill=(190, 230, 255, 38))
    im.alpha_composite(hl)


def draw_header(im):
    # logo mahkota
    rrect(im, 7, 5, 18, 16, 3, top=ACT_TOP, bot=ACT_BOT, outline=(214, 244, 255), ow=0.5)
    d = ImageDraw.Draw(im)
    crown = [(8.9, 13.4), (8.6, 8.6), (10.6, 10.8), (12.5, 8.0), (14.4, 10.8), (16.4, 8.6), (16.1, 13.4)]
    d.polygon([(x * K, y * K) for x, y in crown], fill=(255, 255, 255, 255))
    d.rounded_rectangle(bx(8.9, 13.9, 16.1, 14.9), radius=0.4 * K, fill=(255, 255, 255, 255))
    text(im, "PILLAR OF FORTUNE", 22, 13.2, 6.0, (255, 255, 255), path=INTER_X, track=0.15)
    text(im, "PILAR + ITEM ACAK", 169, 12.6, 3.0, DIM, anchor="r")


def draw_tabs(im):
    for i, (x0, x1) in enumerate(TABS):
        y = row_y(0)
        label = ["SETTING", "PEMAIN", "LOBBY"][i]
        if i == 0:
            glow(im, x0, y, x1, y + 16, 8, (80, 190, 255), strength=100, blur=1.6)
            rrect(im, x0, y, x1, y + 16, 8, top=ACT_TOP, bot=ACT_BOT, outline=(214, 244, 255), ow=0.6)
            col = ACT_TXT
        else:
            rrect(im, x0, y, x1, y + 16, 8, fill=(8, 22, 50), outline=(46, 108, 186), ow=0.7)
            col = DIM
        text(im, str(i + 1), x0 + 8, y + 10.5, 5.0, col, anchor="m", path=INTER_X)
        text(im, label, (x0 + x1) / 2 + 5, y + 10.2, 4.6, col, anchor="m", track=0.2)


def draw_stepper(im, x0, x1, r, label):
    y = row_y(r)
    rrect(im, x0, y, x1, y + 16, 5, fill=INPUT, outline=INPUT_EDGE, ow=0.7)
    d = ImageDraw.Draw(im)
    for xl in (x0 + 16, x1 - 16):
        d.line([(xl * K, (y + 3) * K), (xl * K, (y + 13) * K)], fill=INPUT_EDGE + (90,), width=max(1, K // 2))
    tri(im, x0 + 8, y + 8, -1)
    tri(im, x1 - 8, y + 8, +1)
    text(im, label, (x0 + x1) / 2, y + 5.1, 2.9, DIM, anchor="m", track=0.25)


def draw_speed_base(im):
    y = row_y(2)
    text(im, "SPEED BORDER", 10, y + 10.1, 4.4, TXT, track=0.15)
    rrect(im, SPEED_BOX[0], y, SPEED_BOX[1], y + 16, 5, fill=INPUT, outline=INPUT_EDGE, ow=0.7)
    for (x0, x1), n in zip(SPEED_SEG, SPEED_NAMES):
        text(im, n, (x0 + x1) / 2, y + 10.3, 4.4, DIM, anchor="m", track=0.2)


def draw_speed_sel(im, i):
    y = row_y(2)
    x0, x1 = SPEED_SEG[i]
    glow(im, x0, y + 1, x1, y + 15, 4, (80, 190, 255), strength=90, blur=1.4)
    rrect(im, x0, y + 1, x1, y + 15, 4, top=ACT_TOP, bot=ACT_BOT, outline=(214, 244, 255), ow=0.5)
    text(im, SPEED_NAMES[i], (x0 + x1) / 2, y + 10.3, 4.4, ACT_TXT, anchor="m", track=0.2, path=INTER_X)


def draw_chip_base(im, i):
    x0, x1 = CHIPS[i]
    y = row_y(4)
    rrect(im, x0, y, x1, y + 16, 5, fill=(9, 22, 48), outline=(38, 92, 164), ow=0.7)
    # switch OFF
    sx, sy = x0 + 3.5, y + 5.2
    rrect(im, sx, sy, sx + 10, sy + 5.6, 2.8, fill=OFF_TRACK, outline=(70, 112, 170), ow=0.4)
    d = ImageDraw.Draw(im)
    d.ellipse(bx(sx + 0.8, sy + 0.8, sx + 4.8, sy + 4.8), fill=OFF_KNOB + (255,))
    l1, l2 = CHIP_TXT[i]
    tx = x0 + 16.5
    text(im, l1, tx, y + 7.0, 3.1, TXT, track=0.1)
    text(im, l2, tx, y + 12.4, 3.1, TXT, track=0.1)


def draw_chip_on(im, i):
    x0, x1 = CHIPS[i]
    y = row_y(4)
    rrect(im, x0, y, x1, y + 16, 5, fill=(10, 28, 60), outline=(96, 190, 255), ow=0.8)
    sx, sy = x0 + 3.5, y + 5.2
    glow(im, sx, sy, sx + 10, sy + 5.6, 2.8, (80, 190, 255), strength=120, blur=1.2)
    rrect(im, sx, sy, sx + 10, sy + 5.6, 2.8, top=ACT_TOP, bot=ACT_BOT, outline=(214, 244, 255), ow=0.4)
    d = ImageDraw.Draw(im)
    d.ellipse(bx(sx + 5.2, sy + 0.8, sx + 9.2, sy + 4.8), fill=(255, 255, 255, 255))
    l1, l2 = CHIP_TXT[i]
    tx = x0 + 16.5
    text(im, l1, tx, y + 7.0, 3.1, (255, 255, 255), track=0.1)
    text(im, l2, tx, y + 12.4, 3.1, (255, 255, 255), track=0.1)


def draw_cta(im):
    y = row_y(5)
    x0, x1 = BATAL
    rrect(im, x0, y, x1, y + 16, 5, fill=(12, 20, 46), outline=RED, ow=0.8)
    text(im, "BATAL", (x0 + x1) / 2, y + 10.4, 4.8, RED, anchor="m", path=INTER_X, track=0.25)
    x0, x1 = LANJUT
    glow(im, x0, y, x1, y + 16, 5, (80, 190, 255), strength=110, blur=1.6)
    rrect(im, x0, y, x1, y + 16, 5, top=ACT_TOP, bot=ACT_BOT, outline=(220, 246, 255), ow=0.7)
    s = "LANJUT"
    total = tw(s, 5.2, INTER_X, 0.25) + 3 + 8
    sx = (x0 + x1) / 2 - total / 2
    text(im, s, sx, y + 10.5, 5.2, ACT_TXT, path=INTER_X, track=0.25)
    ax = sx + total - 8
    d = ImageDraw.Draw(im)
    cy = (y + 8) * K
    d.line([(ax * K, cy), ((ax + 6.4) * K, cy)], fill=ACT_TXT + (255,), width=round(1.3 * K))
    d.line([((ax + 3.8) * K, cy - 2.4 * K), ((ax + 6.6) * K, cy)], fill=ACT_TXT + (255,), width=round(1.3 * K))
    d.line([((ax + 3.8) * K, cy + 2.4 * K), ((ax + 6.6) * K, cy)], fill=ACT_TXT + (255,), width=round(1.3 * K))


def draw_tray(im):
    """Area inventory pemain (chest 6 baris selalu nampilin inventory di bawah)."""
    rrect(im, 3, 131, 173, 219, 6, fill=(5, 13, 32), outline=(26, 68, 124), ow=0.8, alpha=235)
    cells = [(8 + 18 * c, 139 + 18 * r) for r in range(3) for c in range(9)] + [(8 + 18 * c, 197) for c in range(9)]
    for x, y in cells:
        rrect(im, x - 0.5, y - 0.5, x + 16.5, y + 16.5, 2.5, fill=(10, 26, 54), outline=(24, 62, 112), ow=0.5)


def build(kind=None, idx=0):
    """kind None = dasar; 'speed' / 'tog' = layer overlay."""
    im = new()
    if kind is None:
        draw_background(im)
        draw_card(im)
        draw_header(im)
        draw_tabs(im)
        draw_stepper(im, *CARD_L, 1, "ITEM TIAP")
        draw_stepper(im, *CARD_R, 1, "BORDER MULAI")
        draw_speed_base(im)
        draw_stepper(im, *RONDE, 3, "RONDE")
        draw_stepper(im, *MAP, 3, "MAP")
        for i in range(3):
            draw_chip_base(im, i)
        draw_cta(im)
        draw_tray(im)
    elif kind == "speed":
        draw_speed_sel(im, idx)
    elif kind == "tog":
        draw_chip_on(im, idx)
    return im.resize((W * OUT, H * OUT), Image.LANCZOS)


# ================= ATLAS TEKS DINAMIS =================
CELL_W, CELL_H = 12, 20
TEXT_ROWS = ["0123456789ABCDEF", "GHIJKLMNOPQRSTUV", "WXYZsm" + "\u0000" * 10]
TEXT_ADV = CELL_W // 2 + 1        # = 8 px GUI per huruf
TEXT_HEIGHT = CELL_H // 2         # = 10 px GUI


def build_text_atlas():
    atlas = Image.new("RGBA", (16 * CELL_W, 3 * CELL_H), (0, 0, 0, 0))
    big = 4
    f = ImageFont.truetype(INTER_X, round(12 * big / CAP))
    for r, row in enumerate(TEXT_ROWS):
        for c, ch in enumerate(row):
            if ch == "\u0000":
                continue
            cell = Image.new("RGBA", (CELL_W * big, CELL_H * big), (0, 0, 0, 0))
            tmp = Image.new("RGBA", (CELL_W * big * 2, CELL_H * big), (0, 0, 0, 0))
            ImageDraw.Draw(tmp).text((CELL_W * big, 16 * big), ch, font=f, fill=(255, 255, 255, 255), anchor="ms")
            bbox = tmp.getbbox()
            g = tmp.crop(bbox)
            maxw = (CELL_W - 1) * big
            if g.width > maxw:
                g = g.resize((maxw, g.height), Image.LANCZOS)
            x = (CELL_W * big - g.width) // 2
            cell.paste(g, (x, bbox[1]))
            cell = cell.resize((CELL_W, CELL_H), Image.LANCZOS)
            atlas.paste(cell, (c * CELL_W, r * CELL_H))
            atlas.putpixel((c * CELL_W + CELL_W - 1, r * CELL_H), (255, 255, 255, 1))
    return atlas


# ================= FONT JSON =================
def space_provider(extra_space=None):
    adv = {" ": extra_space or 4}
    for k in range(10):
        adv[chr(0xF800 + k)] = -(2 ** k)
        adv[chr(0xF820 + k)] = 2 ** k
    return {"type": "space", "advances": adv}


def text_font(row):
    ascent = 13 - (row_y(row) + 6)          # glyph top = 13 - ascent (judul chest di y=6, ascent baseline 7)
    return {"providers": [
        {"type": "space", "advances": {" ": TEXT_ADV}},
        {"type": "bitmap", "file": "zetminigames:font/gui_text.png", "ascent": ascent, "height": TEXT_HEIGHT,
         "chars": TEXT_ROWS}]}


TILE_W, TILE_H = W * OUT // 2, H * OUT // 2     # 176 x 222 px texture = 88 x 111 px GUI


def tiles(layer):
    """Potong layer 352x444 jadi 4 tile: (kiri-atas, kanan-atas, kiri-bawah, kanan-bawah)."""
    out = []
    for ty in range(2):
        for tx in range(2):
            t = layer.crop((tx * TILE_W, ty * TILE_H, (tx + 1) * TILE_W, (ty + 1) * TILE_H))
            # penanda lebar (alpha 1) di kolom paling kanan, kecuali pixel itu sudah terisi
            if t.getpixel((TILE_W - 1, 0))[3] == 0:
                t.putpixel((TILE_W - 1, 0), (255, 255, 255, 1))
            out.append(t)
    return out


def main():
    layers = [build(), build("speed", 0), build("speed", 1), build("speed", 2),
              build("tog", 0), build("tog", 1), build("tog", 2)]
    top, bot = [], []
    for i, l in enumerate(layers):
        tl, tr, bl, br = tiles(l)
        top += [tl, tr]
        if i == 0:
            bot += [bl, br]
        else:
            # overlay cuma ada di baris atas (kontrol ada di y < 111); pastikan memang begitu
            assert bl.getbbox() is None or bl.getbbox() == (0, 0, TILE_W, 1) or bl.getchannel("A").point(lambda v: 255 if v > 1 else 0).getbbox() is None
            assert br.getchannel("A").point(lambda v: 255 if v > 1 else 0).getbbox() is None
    atlas = Image.new("RGBA", (TILE_W * len(top), TILE_H), (0, 0, 0, 0))
    for i, t in enumerate(top):
        atlas.paste(t, (i * TILE_W, 0))
    atlas.save("gui_layers.png")
    atlas_b = Image.new("RGBA", (TILE_W * 2, TILE_H), (0, 0, 0, 0))
    for i, t in enumerate(bot):
        atlas_b.paste(t, (i * TILE_W, 0))
    atlas_b.save("gui_layers_b.png")
    top_chars = "".join(chr(0xE100 + i) for i in range(len(top)))
    json.dump({"providers": [
        space_provider(),
        {"type": "bitmap", "file": "zetminigames:font/gui_layers.png",
         "ascent": 13, "height": H // 2, "chars": [top_chars]}]}, open("gui.json", "w"), indent=2)
    json.dump({"providers": [
        {"type": "bitmap", "file": "zetminigames:font/gui_layers_b.png",
         "ascent": 13 - H // 2, "height": H // 2, "chars": ["\ue100\ue101"]}]}, open("gui_b.json", "w"), indent=2)

    build_text_atlas().save("gui_text.png")
    json.dump(text_font(1), open("gui_v1.json", "w"), indent=2)
    json.dump(text_font(3), open("gui_v3.json", "w"), indent=2)

    # hover slot: tile cyan tipis 16x16 di tengah sprite 24x24 (vanilla: back + front)
    back = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    back.save("gui_slot_back.png")
    front = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    ImageDraw.Draw(front).rounded_rectangle([4, 4, 19, 19], radius=3, fill=(150, 225, 255, 70), outline=(190, 240, 255, 150))
    front.save("gui_slot_front.png")

    make_preview(layers)
    print("ok - gui blue glass generated")


# ================= PREVIEW (simulasi kasar tampilan di game + hitbox) =================
def make_preview(layers, speed=1, toggles=(False, True, False), values=None, hitbox=False):
    values = values or {"v1": [(43, "60s"), (133, "5m")], "v3": [(43, "1"), (124, "RANDOM")]}
    img = layers[0].copy()
    img.alpha_composite(layers[1 + speed])
    for i, on in enumerate(toggles):
        if on:
            img.alpha_composite(layers[4 + i])
    atlas = build_text_atlas()
    pos = {}
    for r, row in enumerate(TEXT_ROWS):
        for c, ch in enumerate(row):
            if ch != "\u0000":
                pos[ch] = (c * CELL_W, r * CELL_H)
    for key, row in (("v1", 1), ("v3", 3)):
        y_top = row_y(row) + 6
        for cx, s in values[key]:
            x = round(cx - (TEXT_ADV * len(s) - 1) / 2)
            for ch in s:
                px, py = pos[ch]
                cell = atlas.crop((px, py, px + CELL_W, py + CELL_H))
                tinted = Image.new("RGBA", cell.size, CYAN_L + (255,))
                tinted.putalpha(cell.getchannel("A"))
                img.alpha_composite(tinted, (x * OUT, y_top * OUT))
                x += TEXT_ADV
    if hitbox:
        d = ImageDraw.Draw(img)
        for r in range(6):
            for c in range(9):
                x, y = 8 + 18 * c, row_y(r)
                d.rectangle([x * OUT, y * OUT, (x + 16) * OUT - 1, (y + 16) * OUT - 1], outline=(255, 60, 60, 220))
    big = img.resize((img.width * 2, img.height * 2), Image.NEAREST)
    bgc = Image.new("RGBA", big.size, (120, 120, 120, 255))
    bgc.alpha_composite(big)
    bgc.save("gui_preview.png" if not hitbox else "gui_preview_hitbox.png")


if __name__ == "__main__":
    main()
    make_preview([build(), build("speed", 0), build("speed", 1), build("speed", 2),
                  build("tog", 0), build("tog", 1), build("tog", 2)], hitbox=True)
