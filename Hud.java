package id.zetminigames.hud;

import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Bikin tampilan HUD (title bossbar) pakai font custom dari resource pack (zetminigames:hud).
 * Semua posisi dihitung dalam pixel relatif ke sisi kiri panel.
 */
public final class Hud {
    private Hud() {}

    public static final Key FONT = Key.key("zetminigames", "hud");
    /** Font kedua: ascii + ikon border digambar lebih ke bawah (baris ke-4 panel). */
    public static final Key FONT_LOW = Key.key("zetminigames", "hud_low");
    public static final String ICON_BORDER = "\uE00A";
    /** Font ketiga: ascii digambar di baris paling atas (sejajar baris kepala). */
    public static final Key FONT_TOP = Key.key("zetminigames", "hud_top");
    /** Font HUD bawah (di atas hotbar, dikirim lewat actionbar): panel + ikon api + ascii normal. */
    public static final Key FONT_BOTTOM = Key.key("zetminigames", "hud_bottom");
    public static final String BOTTOM_PANEL = "\uE060";
    public static final String ICON_LAVA = "\uE061";
    public static final int BOTTOM_W = 120;
    // ikon Battle Royale (hud_br.png)
    public static final String ICON_ZONE = "\uE040";
    public static final String ICON_PVP = "\uE041";
    public static final String ICON_KILLS = "\uE042";
    public static final String ICON_WARN = "\uE043";
    public static final String ICON_CLOCK = "\uE044";
    /** Panah arah ke zona: 8 arah, urutan searah jarum jam mulai dari atas (depan). */
    public static final int ARROW_BASE = 0xE050;
    public static final String[] ARROW_TEXT = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};

    public static final String PANEL = "\uE000";
    /** Panel Battle Royale: 3 baris (tanpa baris border). */
    public static final String PANEL_BR = "\uE00D";
    public static final String SKULL = "\uE001";
    public static final String FRAME = "\uE004";
    public static final String LOGO = "\uE006";
    public static final String BANNER = "\uE008";
    public static final String WINFRAME = "\uE009";
    public static final String BANNER_ROUND = "\uE00B";
    public static final String BANNER_DRAW = "\uE00C";
    // ikon tombol dialog (sprite sheet hud_icons.png)
    public static final String ICO_TROPHY = "\uE020";
    public static final String ICO_USERS = "\uE021";
    public static final String ICO_FLAG = "\uE022";
    public static final String ICO_CHECK = "\uE023";
    public static final String ICO_CROSS = "\uE024";
    public static final String ICO_PLAY = "\uE025";
    public static final String ICO_BACK = "\uE026";
    public static final String ICON_ITEM = "\uE002";
    public static final String ICON_ALIVE = "\uE003";
    public static final int BAR_BASE = 0xE010;

    public static final int PANEL_W = 150;
    public static final int GLYPH8_ADV = 9;      // glyph lebar 8 => advance 9
    public static final int BAR_CHUNKS = 13;
    public static final int CHUNK_W = 10;
    public static final int FRAME_W = 10;       // frame kepala 10px => advance 11

    /** Komponen kepala pemain (object component, Minecraft 1.21.9+). */
    public static Component head(Player p) {
        PlayerHeadObjectContents.Builder b = ObjectContents.playerHead()
                .id(p.getUniqueId())
                .name(p.getName());
        try {
            for (ProfileProperty pp : p.getPlayerProfile().getProperties()) {
                if ("textures".equals(pp.getName())) {
                    b.profileProperty(PlayerHeadObjectContents.property(pp.getName(), pp.getValue(), pp.getSignature()));
                }
            }
        } catch (Exception ignored) {
        }
        return Component.object(b.build());
    }

    /**
     * @param heads     komponen kepala per pemain (urutan pilar)
     * @param alive     status hidup per pemain
     * @param headAdv   lebar advance object kepala (tuning lewat config)
     * @param slot      lebar satu slot kepala (kepala + jarak)
     * @param frac      0..1 progress menuju item berikutnya
     */
    public static Component build(List<Component> heads, boolean[] alive, int aliveCount, int total,
                                  int nextItemSec, double frac, int headAdv, int slot,
                                  int borderSec, boolean shrinking, String roundLabel, int clockSec,
                                  String topOverride) {
        return compose(heads, alive, aliveCount, total,
                topOverride != null ? topOverride : "ITEM " + nextItemSec + "d", frac, headAdv, slot,
                borderSec, shrinking, roundLabel, true, clockSec);
    }

    public static Component buildSwap(List<Component> heads, boolean[] alive, int aliveCount, int total,
                                      int swapSec, double frac, int headAdv, int slot, String roundLabel,
                                      String topOverride, int clockSec) {
        return compose(heads, alive, aliveCount, total,
                topOverride != null ? topOverride : "TUKAR " + fmtTime(swapSec), frac, headAdv, slot,
                0, false, roundLabel, false, clockSec);
    }

    public static Component buildMace(List<Component> heads, boolean[] alive, int aliveCount, int total,
                                      String topText, double frac, int headAdv, int slot, String roundLabel,
                                      int clockSec) {
        return compose(heads, alive, aliveCount, total, topText, frac, headAdv, slot,
                0, false, roundLabel, false, clockSec);
    }

    private static Component compose(List<Component> heads, boolean[] alive, int aliveCount, int total,
                                     String topText, double frac, int headAdv, int slot,
                                     int borderSec, boolean shrinking, String roundLabel, boolean borderRow,
                                     int clockSec) {
        Row r = new Row();
        r.glyph(PANEL, PANEL_W + 1);

        // Baris 1: frame + kepala pemain (mati = tengkorak nutup kepala)
        int n = heads.size();
        int sl = Math.max(slot, FRAME_W);
        int xHeads = (PANEL_W - (n * sl - (sl - FRAME_W))) / 2;
        int maxHeads = (PANEL_W - FRAME_W) / sl + 1;   // kebanyakan pemain: baris kepala dilewati
        for (int i = 0; i < n && n <= maxHeads; i++) {
            int x0 = xHeads + i * sl;
            r.shiftTo(x0);
            r.glyph(FRAME, FRAME_W + 1);
            r.shiftTo(x0 + 1);
            if (alive[i]) r.raw(heads.get(i), headAdv);
            else r.glyph(SKULL, GLYPH8_ADV);
        }

        // Baris 2: NEXT ITEM di kiri, jumlah hidup di kanan
        r.shiftTo(6);
        r.glyph(ICON_ITEM, GLYPH8_ADV);
        r.shift(2);
        r.text(topText, TextColor.color(0xFFFFFF));

        String cnt = aliveCount + "/" + total;
        int xText = PANEL_W - 7 - width(cnt);
        int xIcon = xText - 2 - GLYPH8_ADV;
        r.shiftTo(xIcon);
        r.glyph(ICON_ALIVE, GLYPH8_ADV);
        r.shift(2);
        r.text(cnt, TextColor.color(0xFFFFFF));

        // Baris 3: bar kuning (glyph per 10px, 11 variasi isi)
        int totalPx = BAR_CHUNKS * CHUNK_W;
        int fillPx = (int) Math.round(Math.max(0.0, Math.min(1.0, frac)) * totalPx);
        r.shiftTo((PANEL_W - totalPx) / 2);
        for (int i = 0; i < BAR_CHUNKS; i++) {
            int fill = Math.max(0, Math.min(CHUNK_W, fillPx - i * CHUNK_W));
            r.glyph(String.valueOf((char) (BAR_BASE + fill)), CHUNK_W + 1);
            r.shift(-1);
        }

        // Baris 4: BORDER di kiri; label ronde + jam (timer game) di kanan
        boolean hasLabel = roundLabel != null && !roundLabel.isEmpty();
        if (borderRow) {
            r.shiftTo(6);
            r.glyph(ICON_BORDER, GLYPH8_ADV, FONT_LOW);
            r.shift(2);
            if (shrinking) {
                r.text(hasLabel && clockSec >= 0 ? "KECIL!" : "MENGECIL!", TextColor.color(0xFF5555), FONT_LOW);
            } else {
                if (!hasLabel || clockSec < 0) r.text("BORDER ", TextColor.color(0x80C8FF), FONT_LOW);
                r.text(fmtTime(borderSec), TextColor.color(0xFFFFFF), FONT_LOW);
            }
        }
        if (clockSec >= 0) {
            String t = clock(clockSec);
            int xTime = PANEL_W - 7 - width(t);
            int xClock = xTime - 2 - GLYPH8_ADV;
            if (hasLabel) {
                r.shiftTo(xClock - 4 - width(roundLabel));
                r.text(roundLabel, TextColor.color(0x5AD2FF), FONT_LOW);
            }
            r.shiftTo(xClock);
            r.glyph(ICON_CLOCK, GLYPH8_ADV, FONT_LOW);
            r.shift(2);
            r.text(t, TextColor.color(0xFFFFFF), FONT_LOW);
        } else if (hasLabel) {
            r.shiftTo(PANEL_W - 6 - width(roundLabel));
            r.text(roundLabel, TextColor.color(0x5AD2FF), FONT_LOW);
        }

        // Total lebar = lebar panel, biar tetap center
        r.shiftTo(PANEL_W + 1);
        return r.out.shadowColor(ShadowColor.none());
    }


    /** Data HUD Battle Royale (satu objek per pemain per update). */
    public record BrData(String phase, int phaseColor, String timer, double frac,
                         String pvp, int pvpColor, int alive, int total,
                         int arrow, String distText, int distColor, int kills, boolean test) {}

    /**
     * HUD Battle Royale (panel 4 baris):
     * baris 1 = fase zona (kiri) + timer (kanan), baris 2 = PVP (kiri) + jumlah hidup (kanan),
     * baris 3 = bar waktu zona, baris 4 = panah + jarak ke zona (kiri) + kill (kanan).
     */
    public static Component buildBr(BrData d) {
        Row r = new Row();
        r.glyph(PANEL_BR, PANEL_W + 1);

        // Baris 1 (font atas): fase zona + timer game
        r.shiftTo(6);
        r.glyph(ICON_ZONE, GLYPH8_ADV, FONT_TOP);
        r.shift(2);
        r.text(d.phase(), TextColor.color(d.phaseColor()), FONT_TOP);
        r.shiftTo(PANEL_W - 6 - width(d.timer()));
        r.text(d.timer(), TextColor.color(0xFFFFFF), FONT_TOP);

        // Baris 2 (font utama): PVP + jumlah hidup
        r.shiftTo(6);
        r.glyph(ICON_PVP, GLYPH8_ADV);
        r.shift(2);
        r.text(d.pvp(), TextColor.color(d.pvpColor()));
        if (d.test()) {
            r.shiftTo(72);
            r.text("[TES]", TextColor.color(0xFFAA00));
        }
        String cnt = d.alive() + "/" + d.total();
        int xText = PANEL_W - 7 - width(cnt);
        r.shiftTo(xText - 2 - GLYPH8_ADV);
        r.glyph(ICON_ALIVE, GLYPH8_ADV);
        r.shift(2);
        r.text(cnt, TextColor.color(0xFFFFFF));

        // Baris 3: bar kuning (isi = sisa waktu fase)
        int totalPx = BAR_CHUNKS * CHUNK_W;
        int fillPx = (int) Math.round(Math.max(0.0, Math.min(1.0, d.frac())) * totalPx);
        r.shiftTo((PANEL_W - totalPx) / 2);
        for (int i = 0; i < BAR_CHUNKS; i++) {
            int fill = Math.max(0, Math.min(CHUNK_W, fillPx - i * CHUNK_W));
            r.glyph(String.valueOf((char) (BAR_BASE + fill)), CHUNK_W + 1);
            r.shift(-1);
        }

        r.shiftTo(PANEL_W + 1);
        return r.out.shadowColor(ShadowColor.none());
    }

    /** HUD bawah Battle Royale (di atas hotbar): panah + jarak ke zona di kiri, jumlah kill di kanan. */
    public static Component buildBrBottom(BrData d) {
        Row r = new Row();
        r.glyph(BOTTOM_PANEL, BOTTOM_W + 1, FONT_BOTTOM);
        r.shiftTo(6);
        int a = Math.floorMod(d.arrow(), 8);
        r.raw(Component.text(String.valueOf((char) (ARROW_BASE + a)), TextColor.color(d.distColor())).font(FONT_BOTTOM), GLYPH8_ADV);
        r.shift(2);
        r.text(d.distText(), TextColor.color(d.distColor()), FONT_BOTTOM);
        String kills = String.valueOf(d.kills());
        r.shiftTo(BOTTOM_W - 6 - width(kills) - 2 - GLYPH8_ADV);
        r.glyph(ICON_KILLS, GLYPH8_ADV, FONT_BOTTOM);
        r.shift(2);
        r.text(kills, TextColor.color(0xFFFFFF), FONT_BOTTOM);
        r.shiftTo(BOTTOM_W + 1);
        return r.out.shadowColor(ShadowColor.none());
    }

    /**
     * HUD bawah (di atas hotbar): panel kecil + ikon api + teks berwarna, rata tengah.
     * Dikirim lewat actionbar, tapi isinya glyph font custom dari resource pack (bukan teks actionbar biasa).
     */
    public static Component buildBottom(String text, int color) {
        Row r = new Row();
        r.glyph(BOTTOM_PANEL, BOTTOM_W + 1, FONT_BOTTOM);
        int content = GLYPH8_ADV + 3 + width(text);
        r.shiftTo(Math.max(4, (BOTTOM_W - content) / 2));
        r.glyph(ICON_LAVA, GLYPH8_ADV, FONT_BOTTOM);
        r.shift(3);
        r.text(text, TextColor.color(color), FONT_BOTTOM);
        r.shiftTo(BOTTOM_W + 1);
        return r.out.shadowColor(ShadowColor.none());
    }

    private static void out(Row r, Component c, int adv) {
        r.raw(c, adv);
    }

    /** Lebar teks ASCII vanilla (perkiraan; cukup buat posisi kanan). */
    public static int width(String s) {
        int w = 0;
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case ' ', 'I' -> w += 4;
                case 'i', '!', '.', ',', ':', ';', '|' -> w += 2;
                case 'l', '\'' -> w += 3;
                case 't' -> w += 4;
                case 'f', 'k' -> w += 5;
                default -> w += 6;
            }
        }
        return w;
    }

    /** m:ss (selalu pakai menit), buat jam / timer game. */
    public static String clock(int s) {
        s = Math.max(0, s);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    public static String fmtTime(int s) {
        if (s >= 60) return (s / 60) + ":" + String.format("%02d", s % 60);
        return s + "s";
    }

    /** Karakter spasi (geser) dari font: bit ke-k => +/- 2^k pixel. */
    public static String shiftChars(int px) {
        StringBuilder sb = new StringBuilder();
        boolean neg = px < 0;
        int v = Math.abs(px);
        for (int k = 0; k < 10; k++) {
            if (((v >> k) & 1) == 1) sb.append((char) ((neg ? 0xF800 : 0xF820) + k));
        }
        return sb.toString();
    }

    private static final class Row {
        Component out = Component.empty();
        int c = 0;

        void glyph(String s, int adv) {
            glyph(s, adv, FONT);
        }

        void glyph(String s, int adv, Key font) {
            out = out.append(Component.text(s).font(font));
            c += adv;
        }

        void raw(Component comp, int adv) {
            out = out.append(comp);
            c += adv;
        }

        void shift(int d) {
            if (d == 0) return;
            out = out.append(Component.text(shiftChars(d)).font(FONT));
            c += d;
        }

        void shiftTo(int x) {
            shift(x - c);
        }

        void text(String s, TextColor color) {
            text(s, color, FONT);
        }

        void text(String s, TextColor color, Key font) {
            out = out.append(Component.text(s, color).font(font));
            c += width(s);
        }
    }
}

