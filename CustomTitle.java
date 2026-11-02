package id.zetminigames.hud;

import id.zetminigames.ZetMinigames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

public final class CustomTitle {
    private CustomTitle() {}

    public static final String BANNER_GO = "\uE03A";
    public static final String BANNER_SWAP = "\uE03B";
    public static final String BANNER_ELIM = "\uE03C";
    public static final String BANNER_READY = "\uE03D";

    private static Component glyph(String s) {
        return Component.text(s).font(Hud.FONT).shadowColor(ShadowColor.none());
    }

    public static Component count(int n) {
        int i = Math.max(1, Math.min(5, n));
        return glyph(String.valueOf((char) (0xE030 + i - 1)));
    }

    public static Component tpWarn(int n) {
        int i = Math.max(1, Math.min(5, n));
        return glyph(String.valueOf((char) (0xE035 + i - 1)));
    }

    public static Component banner(String code) {
        return glyph(code);
    }

    public static void show(ZetMinigames plugin, Player p, Component custom,
                            Component fallbackTitle, Component fallbackSub, Title.Times times) {
        if (plugin.packReady(p)) {
            p.showTitle(Title.title(custom, Component.empty(), times));
        } else {
            p.showTitle(Title.title(fallbackTitle, fallbackSub, times));
        }
    }
}
