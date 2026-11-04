package id.zetminigames.panel;

import id.zetminigames.ZetMinigames;
import id.zetminigames.hud.Hud;
import id.zetminigames.pof.PofMap;
import id.zetminigames.pof.ShrinkSpeed;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI custom "Setting Pillar of Fortune" (tema blue glass).
 *
 * Cara kerja: inventory chest 6 baris tanpa item. Gambar GUI digambar lewat glyph font di JUDUL inventory
 * (font zetminigames:gui dari resource pack), slot kosong cuma jadi hitbox klik.
 * Geometri (pixel GUI): slot baris r kolom c = x 8+18c, y 18+18r, ukuran 16x16.
 * Aset dibuat oleh gen_gui.py, jadi kalau layout di sana berubah, tabel klik di {@link #click} harus ikut.
 *
 * Cuma dipakai kalau pemain udah load resource pack (lihat {@link #enabled}); kalau belum, Panels
 * balik ke Dialog biasa.
 */
public final class PofGui implements InventoryHolder {
    static final Key FONT = Key.key("zetminigames", "gui");         // tile baris atas + spasi/geser
    static final Key FONT_B = Key.key("zetminigames", "gui_b");     // tile baris bawah
    static final Key FONT_V1 = Key.key("zetminigames", "gui_v1");   // nilai di baris 1 (item, border)
    static final Key FONT_V3 = Key.key("zetminigames", "gui_v3");   // nilai di baris 3 (ronde, map)

    // Kanvas 176x222 dipotong 2 kolom x 2 baris tile (glyph font maks 256 px). Tiap layer = 2 glyph (kiri, kanan).
    private static final char G_BASE = '';       // dasar: kiri E100, kanan E101 (di FONT dan FONT_B)
    private static final char G_SPEED = '';      // +2*i (pelan, sedang, cepat) terpilih
    private static final char G_TOGGLE = '';     // +2*i (seri, stop, tes) nyala

    /** Lebar satu tile = 88 px GUI. */
    private static final int TILE_W = 88;
    /** Advance satu glyph tile (176 px texture / 2 + 1). Bisa dituning lewat config gui.tile-advance. */
    private static final int DEF_TILE_ADV = 89;
    /** Advance satu huruf font nilai (12 px texture / 2 + 1). Config: gui.text-advance. */
    private static final int DEF_TXT_ADV = 7;
    /** Judul chest mulai di x=8 dari kiri GUI. */
    private static final int TITLE_X = 8;

    private static final TextColor VALUE_COLOR = TextColor.color(0xCFF3FF);

    private static final String[] ITEM_PRESETS = {"15s", "30s", "45s", "60s", "90s", "2m", "3m", "5m"};
    private static final String[] BORDER_PRESETS = {"1m", "2m", "3m", "5m", "8m", "10m", "15m"};
    private static final List<String> MAPS = new ArrayList<>();

    static {
        MAPS.add("RANDOM");
        for (PofMap m : PofMap.values()) MAPS.add(m.name());
    }

    private final ZetMinigames plugin;
    private final Panels.Draft draft;
    private Inventory inv;

    private PofGui(ZetMinigames plugin, Panels.Draft draft) {
        this.plugin = plugin;
        this.draft = draft;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    /** GUI custom aktif kalau pemain udah load pack dan gak dimatiin di config (gui.custom). */
    public static boolean enabled(ZetMinigames plugin, Player p) {
        return plugin.packReady(p) && plugin.getConfig().getBoolean("gui.custom", true);
    }

    public static void open(ZetMinigames plugin, Player host, Panels.Draft d) {
        new PofGui(plugin, d).show(host);
    }

    private void show(Player p) {
        inv = Bukkit.createInventory(this, 54, title());
        p.openInventory(inv);
    }

    // ===================== GAMBAR (judul inventory) =====================

    private Component title() {
        Row r = new Row(TITLE_X,
                plugin.getConfig().getInt("gui.tile-advance", DEF_TILE_ADV),
                plugin.getConfig().getInt("gui.text-advance", DEF_TXT_ADV));
        r.layer(FONT, G_BASE);                       // dasar, baris atas
        r.layer(FONT_B, G_BASE);                     // dasar, baris bawah
        r.layer(FONT, (char) (G_SPEED + 2 * speedIndex()));
        boolean[] on = {draft.draw, draft.skip, draft.test};
        for (int i = 0; i < 3; i++) {
            if (on[i]) r.layer(FONT, (char) (G_TOGGLE + 2 * i));
        }
        r.value(ITEM_PRESETS[itemIndex()], FONT_V1, 43);
        r.value(BORDER_PRESETS[borderIndex()], FONT_V1, 133);
        r.value(String.valueOf(rounds()), FONT_V3, 43);
        r.value(draft.map == null ? "RANDOM" : draft.map, FONT_V3, 124);
        return r.out.shadowColor(ShadowColor.none());
    }

    /** Susun komponen judul: geser pakai karakter spasi font (bisa negatif), gambar pakai glyph. */
    private static final class Row {
        Component out = Component.empty();
        int c;
        final int tileAdv;
        final int txtAdv;

        Row(int start, int tileAdv, int txtAdv) {
            this.c = start;
            this.tileAdv = tileAdv;
            this.txtAdv = txtAdv;
        }

        void shiftTo(int x) {
            int d = x - c;
            if (d == 0) return;
            out = out.append(Component.text(Hud.shiftChars(d)).font(FONT));
            c += d;
        }

        /** Satu layer = 2 glyph tile (kiri di x=0, kanan di x=88), char kanan = char kiri + 1. */
        void layer(Key font, char left) {
            shiftTo(0);
            out = out.append(Component.text(String.valueOf(left), NamedTextColor.WHITE).font(font));
            c += tileAdv;
            shiftTo(TILE_W);
            out = out.append(Component.text(String.valueOf((char) (left + 1)), NamedTextColor.WHITE).font(font));
            c += tileAdv;
        }

        /** Teks nilai, rata tengah di x = center (pixel GUI dari kiri kanvas). */
        void value(String s, Key font, int center) {
            int x = (int) Math.round(center - (txtAdv * s.length() - 1) / 2.0);
            shiftTo(x);
            out = out.append(Component.text(s, VALUE_COLOR).font(font));
            c += txtAdv * s.length();
        }
    }

    // ===================== STATE =====================

    private int speedIndex() {
        ShrinkSpeed s = ShrinkSpeed.fromId(draft.speed);
        return s.ordinal();
    }

    private int itemIndex() {
        return nearest(ITEM_PRESETS, Panels.parseTime(draft.itemText));
    }

    private int borderIndex() {
        return nearest(BORDER_PRESETS, Panels.parseTime(draft.borderText));
    }

    /** Index preset yang detiknya paling dekat (draft dari Dialog bisa berisi teks bebas). */
    private static int nearest(String[] presets, int sec) {
        int best = 0;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < presets.length; i++) {
            int diff = Math.abs(Panels.parseTime(presets[i]) - sec);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    private int rounds() {
        try {
            return Math.max(1, Math.min(10, Integer.parseInt(draft.rounds.trim())));
        } catch (Exception e) {
            return 1;
        }
    }

    private static int wrap(int idx, int d, int n) {
        return Math.floorMod(idx + d, n);
    }

    // ===================== KLIK =====================

    /** Jalan di tick berikutnya (bukan di dalam event klik). */
    private void click(Player p, int slot, boolean right) {
        int r = slot / 9;
        int c = slot % 9;
        boolean changed = true;
        switch (r) {
            case 0 -> {
                // tab: 1 SETTING (sini), 2 PEMAIN (lanjut), 3 LOBBY (belum ada)
                if (c >= 3 && c <= 5) {
                    next(p);
                    return;
                }
                if (c >= 6) p.sendActionBar(Component.text("Lobby kebuka setelah pilih pemain", NamedTextColor.GRAY));
                changed = false;
            }
            case 1 -> {
                if (c <= 3) {
                    draft.itemText = ITEM_PRESETS[wrap(itemIndex(), dir(c, 0, 3, right), ITEM_PRESETS.length)];
                } else if (c >= 5) {
                    draft.borderText = BORDER_PRESETS[wrap(borderIndex(), dir(c, 5, 8, right), BORDER_PRESETS.length)];
                } else {
                    changed = false;
                }
            }
            case 2 -> {
                if (c >= 3) draft.speed = ShrinkSpeed.values()[(c - 3) / 2].name();
                else changed = false;
            }
            case 3 -> {
                if (c <= 3) {
                    draft.rounds = String.valueOf(wrap(rounds() - 1, dir(c, 0, 3, right), 10) + 1);
                } else {
                    int cur = Math.max(0, MAPS.indexOf(draft.map == null ? "RANDOM" : draft.map));
                    draft.map = MAPS.get(wrap(cur, dir(c, 4, 8, right), MAPS.size()));
                }
            }
            case 4 -> {
                if (c <= 2) draft.draw = !draft.draw;
                else if (c <= 6) draft.skip = !draft.skip;
                else draft.test = !draft.test;
            }
            case 5 -> {
                if (c <= 2) {
                    p.closeInventory();
                    return;
                }
                next(p);
                return;
            }
            default -> changed = false;
        }
        if (!changed) return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
        show(p);
    }

    /** Panah kiri = -1, panah kanan = +1, bagian tengah = +1 (klik kanan = -1). */
    private static int dir(int c, int prevCol, int nextCol, boolean right) {
        if (c == prevCol) return -1;
        if (c == nextCol) return 1;
        return right ? -1 : 1;
    }

    private void next(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
        p.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> Panels.openPlayers(plugin, p, draft, null));
    }

    // ===================== EVENT =====================

    public static final class Events implements Listener {
        private final ZetMinigames plugin;

        public Events(ZetMinigames plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onClick(InventoryClickEvent e) {
            Inventory top = e.getView().getTopInventory();
            if (!(top.getHolder() instanceof PofGui gui)) return;
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (e.getClickedInventory() != top) return;
            if (!e.getClick().isLeftClick() && !e.getClick().isRightClick()) return;
            int slot = e.getSlot();
            if (slot < 0 || slot >= 54) return;
            boolean right = e.getClick().isRightClick();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == gui) {
                    gui.click(p, slot, right);
                }
            });
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getView().getTopInventory().getHolder() instanceof PofGui) e.setCancelled(true);
        }
    }
}
