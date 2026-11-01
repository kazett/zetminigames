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

    public static final String PANEL = "\uE000";
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
                                  int borderSec, boolean shrinking, String roundLabel) {
        return compose(heads, alive, aliveCount, total, "NEXT ITEM " + nextItemSec + "s", frac, headAdv, slot,
                borderSec, shrinking, roundLabel, true);
    }

    public static Component buildSwap(List<Component> heads, boolean[] alive, int aliveCount, int total,
                                      int swapSec, double frac, int headAdv, int slot, String roundLabel) {
        return compose(heads, alive, aliveCount, total, "SWAP TP " + fmtTime(swapSec), frac, headAdv, slot,
                0, false, roundLabel, false);
    }

    public static Component buildMace(List<Component> heads, boolean[] alive, int aliveCount, int total,
                                      String topText, double frac, int headAdv, int slot, String roundLabel) {
        return compose(heads, alive, aliveCount, total, topText, frac, headAdv, slot,
                0, false, roundLabel, false);
    }

    private static Component compose(List<Component> heads, boolean[] alive, int aliveCount, int total,
                                     String topText, double frac, int headAdv, int slot,
                                     int borderSec, boolean shrinking, String roundLabel, boolean borderRow) {
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

        // Baris 4: BORDER (font bawah)
        if (borderRow) {
            r.shiftTo(6);
            r.glyph(ICON_BORDER, GLYPH8_ADV, FONT_LOW);
            r.shift(2);
            if (shrinking) {
                r.text("BORDER MENGECIL!", TextColor.color(0xFF5555), FONT_LOW);
            } else {
                r.text("BORDER ", TextColor.color(0xFF8080), FONT_LOW);
                r.text(fmtTime(borderSec), TextColor.color(0xFFFFFF), FONT_LOW);
            }
        }

        // Label ronde (kanan baris 4), kosong kalau cuma 1 ronde
        if (roundLabel != null && !roundLabel.isEmpty()) {
            r.shiftTo(PANEL_W - 6 - width(roundLabel));
            r.text(roundLabel, TextColor.color(0xFFD23C), FONT_LOW);
        }

        // Total lebar = lebar panel, biar tetap center
        r.shiftTo(PANEL_W + 1);
        return r.out.shadowColor(ShadowColor.none());
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

