package id.zetminigames.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;

/**
 * Layar hasil (dipakai kalau pemain udah load resource pack).
 * Title (skala 4x)    : banner (WINNER / ROUND WIN / SERI) + frame emas + kepala di tengah.
 * Subtitle (skala 2x) : mahkota + nama (simetris => pas di tengah).
 */
public final class WinScreen {
    private WinScreen() {}

    public static final int BANNER_W = 96;     // lebar gambar banner
    public static final int BANNER_ADV = 97;   // banner 96px => advance 97
    public static final int FRAME_ADV = 13;    // frame 12px  => advance 13
    public static final int MAX_HEADS = 5;     // lebih dari ini gak muat di layar

    public static Component title(Component head, int headAdv) {
        return title(Hud.BANNER, List.of(head), headAdv);
    }

    /** Banner di atas, lalu 1..MAX_HEADS kepala (masing-masing dalam frame) di bawahnya, semua center. */
    public static Component title(String banner, List<Component> heads, int headAdv) {
        int k = Math.max(1, Math.min(MAX_HEADS, heads.size()));
        Component out = Component.empty();
        int c = 0;
        out = out.append(glyph(banner));
        c += BANNER_ADV;

        int start = (BANNER_W - (FRAME_ADV * k - 1)) / 2;
        for (int i = 0; i < k && i < heads.size(); i++) {
            int xFrame = start + FRAME_ADV * i;
            out = out.append(shift(xFrame - c)); c = xFrame;
            out = out.append(glyph(Hud.WINFRAME)); c += FRAME_ADV;

            int xHead = xFrame + 2;                          // kepala 8px di dalam frame 12px (ring 2px)
            out = out.append(shift(xHead - c)); c = xHead;
            out = out.append(heads.get(i)); c += headAdv;
        }

        out = out.append(shift(BANNER_ADV - c));             // total lebar = lebar banner => tetap center
        return out.shadowColor(ShadowColor.none());
    }

    public static Component subtitle(String name) {
        return Component.empty()
                .append(glyph(Hud.LOGO))
                .append(Component.text(" " + name + " ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(glyph(Hud.LOGO));
    }

    /** Beberapa nama (seri): "A & B & C", lebih dari 3 jadi "A & B & C +N". */
    public static Component subtitleNames(List<String> names) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(3, names.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append(" & ");
            sb.append(names.get(i));
        }
        if (names.size() > shown) sb.append(" +").append(names.size() - shown);
        return Component.text(sb.toString(), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD);
    }

    private static Component glyph(String s) {
        return Component.text(s).font(Hud.FONT);
    }

    private static Component shift(int px) {
        if (px == 0) return Component.empty();
        return Component.text(Hud.shiftChars(px)).font(Hud.FONT);
    }
}
