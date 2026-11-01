package id.zetminigames.panel;

import id.zetminigames.core.GameSettings;
import id.zetminigames.core.GameType;
import id.zetminigames.hud.Hud;
import id.zetminigames.core.Lobby;
import id.zetminigames.core.Match;
import id.zetminigames.pof.PofMap;
import id.zetminigames.pof.ShrinkSpeed;
import id.zetminigames.ZetMinigames;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Semua menu /panel (Dialog bawaan Minecraft, tanpa resource pack). */
public final class Panels {
    private Panels() {}

    /** Isi form sementara, biar gak ilang pas pindah halaman / error. */
    private static final class Draft {
        String itemText = "60s";
        String borderText = "5m";
        String speed = "SEDANG";
        boolean test = false;
        String testPillars = "4";
        String rounds = "1";
        boolean draw = false;
        boolean skip = true;
        GameType type = GameType.POF;
        String swapText = "5m";
        String maceText = "15s";
        String map = "RANDOM";
    }

    private static Draft draftOf(GameType type) {
        Draft d = new Draft();
        d.type = type;
        return d;
    }

    /** Judul dialog; kalau pack udah ke-load, ada ikon mahkota custom di depannya. */
    private static Component title(ZetMinigames plugin, Player p, String t) {
        Component text = Component.text(t, NamedTextColor.GOLD, TextDecoration.BOLD);
        if (plugin.packReady(p)) {
            return Component.text(Hud.LOGO, NamedTextColor.WHITE).font(Hud.FONT)
                    .append(Component.text(" ")).append(text);
        }
        return text;
    }

    /** Label tombol: ikon custom (kalau pack udah ke-load) + teks. */
    private static Component lab(ZetMinigames plugin, Player p, String icon, Component text) {
        if (!plugin.packReady(p)) return text;
        return Component.empty().append(Component.text(icon, NamedTextColor.WHITE).font(Hud.FONT))
                .append(Component.text(" ")).append(text);
    }

    private static ClickCallback.Options once() {
        return ClickCallback.Options.builder().build();
    }

    // ===================== MENU UTAMA =====================

    public static void openMain(ZetMinigames plugin, Player p) {
        ActionButton pofBtn = ActionButton.create(
                lab(plugin, p, Hud.ICO_TROPHY, Component.text("Pillar of Fortune", NamedTextColor.GOLD, TextDecoration.BOLD)),
                Component.text("Terakhir hidup menang"),
                240,
                DialogAction.customClick((view, audience) ->
                        Bukkit.getScheduler().runTask(plugin, () -> openSettings(plugin, p, draftOf(GameType.POF), null)), once()));

        ActionButton dsBtn = ActionButton.create(
                lab(plugin, p, Hud.ICO_FLAG, Component.text("Deathswap", NamedTextColor.RED, TextDecoration.BOLD)),
                Component.text("Tukar posisi, jebak lawan"),
                240,
                DialogAction.customClick((view, audience) ->
                        Bukkit.getScheduler().runTask(plugin, () -> openSettings(plugin, p, draftOf(GameType.DEATHSWAP), null)), once()));

        ActionButton maceBtn = ActionButton.create(
                lab(plugin, p, Hud.ICO_PLAY, Component.text("Mace Roulette", NamedTextColor.AQUA, TextDecoration.BOLD)),
                Component.text("Mace muter, yang kepilih buru lawan"),
                240,
                DialogAction.customClick((view, audience) ->
                        Bukkit.getScheduler().runTask(plugin, () -> openSettings(plugin, p, draftOf(GameType.MACE), null)), once()));

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title(plugin, p, "GAMES"))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("Pilih game", NamedTextColor.GRAY))))
                        .build())
                .type(DialogType.multiAction(List.of(pofBtn, dsBtn, maceBtn), null, 1)));
        p.showDialog(dialog);
    }

    // ===================== HALAMAN 1: SETTING =====================

    private static void openSettings(ZetMinigames plugin, Player host, Draft d, String error) {
        boolean ds = d.type == GameType.DEATHSWAP;
        boolean mace = d.type == GameType.MACE;
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(d.type.label.toUpperCase(), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        body.add(DialogBody.plainMessage(Component.text(
                "Contoh: 30s, 2m, 1m30s", NamedTextColor.GRAY)));
        if (error != null) {
            body.add(DialogBody.plainMessage(Component.text(error, NamedTextColor.RED, TextDecoration.BOLD)));
        }

        List<DialogInput> inputs = new ArrayList<>();
        if (ds) {
            inputs.add(DialogInput.text("swap", Component.text("Waktu TP (min 10s)"))
                    .initial(d.swapText).width(250).build());
        } else if (!mace) {
            inputs.add(DialogInput.text("item", Component.text("Item tiap"))
                    .initial(d.itemText).width(250).build());
            inputs.add(DialogInput.text("border", Component.text("Border mulai"))
                    .initial(d.borderText).width(250).build());
            List<SingleOptionDialogInput.OptionEntry> speeds = new ArrayList<>();
            for (ShrinkSpeed s : ShrinkSpeed.values()) {
                speeds.add(SingleOptionDialogInput.OptionEntry.create(s.name(),
                        Component.text(s.label),
                        s.name().equals(d.speed)));
            }
            inputs.add(DialogInput.singleOption("speed", Component.text("Kecepatan border"), speeds).build());
        } else {
            inputs.add(DialogInput.text("mace", Component.text("Timer mace (min 5s)"))
                    .initial(d.maceText).width(250).build());
        }
        inputs.add(DialogInput.text("rounds", Component.text("Ronde (1-10)"))
                .initial(d.rounds).width(250).build());
        if (!ds && !mace) {
            List<SingleOptionDialogInput.OptionEntry> maps = new ArrayList<>();
            for (PofMap m : PofMap.values()) {
                maps.add(SingleOptionDialogInput.OptionEntry.create(m.name(),
                        Component.text(m.label, m.color, TextDecoration.BOLD), m.name().equals(d.map)));
            }
            maps.add(SingleOptionDialogInput.OptionEntry.create("RANDOM",
                    Component.text("Random", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD), "RANDOM".equals(d.map)));
            inputs.add(DialogInput.singleOption("map", Component.text("Map"), maps).build());
        }
        inputs.add(DialogInput.bool("draw", Component.text("Boleh seri")).initial(d.draw).build());
        inputs.add(DialogInput.bool("skip", Component.text("Stop kalau udah pasti menang")).initial(d.skip).build());
        inputs.add(DialogInput.bool("test", Component.text("Mode tes")).initial(d.test).build());
        if (!ds && !mace) {
            inputs.add(DialogInput.text("pillars", Component.text("Pilar tes (2-20)"))
                    .initial(d.testPillars).width(250).build());
        }

        ActionButton next = ActionButton.create(
                lab(plugin, host, Hud.ICO_USERS, Component.text("Lanjut", NamedTextColor.GREEN, TextDecoration.BOLD)), null, 200,
                DialogAction.customClick((view, audience) -> {
                    d.rounds = nz(view.getText("rounds"), "1");
                    d.draw = Boolean.TRUE.equals(view.getBoolean("draw"));
                    d.skip = Boolean.TRUE.equals(view.getBoolean("skip"));
                    d.test = Boolean.TRUE.equals(view.getBoolean("test"));
                    if (ds) {
                        d.swapText = nz(view.getText("swap"), "5m");
                    } else if (mace) {
                        d.maceText = nz(view.getText("mace"), "15s");
                    } else {
                        d.itemText = nz(view.getText("item"), "60s");
                        d.borderText = nz(view.getText("border"), "5m");
                        d.speed = nz(view.getText("speed"), "SEDANG");
                        d.map = nz(view.getText("map"), "RANDOM");
                        d.testPillars = nz(view.getText("pillars"), "4");
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (ds) {
                            int swap = parseTime(d.swapText);
                            if (swap < GameSettings.MIN_SWAP_SEC) { openSettings(plugin, host, d, "Waktu TP salah (min 10s)"); return; }
                        } else if (mace) {
                            if (parseTime(d.maceText) < 5) { openSettings(plugin, host, d, "Timer mace salah (min 5s)"); return; }
                        } else {
                            int item = parseTime(d.itemText);
                            int border = parseTime(d.borderText);
                            if (item < 0) { openSettings(plugin, host, d, "Waktu item salah"); return; }
                            if (border < 0) { openSettings(plugin, host, d, "Waktu border salah"); return; }
                        }
                        if (parseRounds(d.rounds) < 0) { openSettings(plugin, host, d, "Ronde harus 1-10"); return; }
                        openPlayers(plugin, host, d, null);
                    });
                }, once()));
        ActionButton cancel = ActionButton.create(lab(plugin, host, Hud.ICO_CROSS, Component.text("Batal", NamedTextColor.RED)), null, 120, null);

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title(plugin, host, "Setting Game"))
                        .body(body).inputs(inputs).build())
                .type(DialogType.confirmation(next, cancel)));
        host.showDialog(dialog);
    }

    // ===================== HALAMAN 2: PILIH PEMAIN =====================

    private static void openPlayers(ZetMinigames plugin, Player host, Draft d, String error) {
        List<Player> others = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(host) || plugin.manager().busy(o.getUniqueId())) continue;
            others.add(o);
            if (others.size() >= 30) break;
        }

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(
                d.test ? "Mode tes: sendiri aja, jangan pilih pemain"
                        : "Pilih " + (d.type.min - 1) + "-" + (d.type.max - 1) + " pemain",
                NamedTextColor.GRAY)));
        if (others.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("Gak ada yang online", NamedTextColor.DARK_GRAY)));
        }
        if (error != null) {
            body.add(DialogBody.plainMessage(Component.text(error, NamedTextColor.RED, TextDecoration.BOLD)));
        }

        List<DialogInput> inputs = new ArrayList<>();
        for (int i = 0; i < others.size(); i++) {
            inputs.add(DialogInput.bool("inv_" + i, Component.text(others.get(i).getName(), NamedTextColor.WHITE))
                    .initial(false).build());
        }

        ActionButton create = ActionButton.create(
                lab(plugin, host, Hud.ICO_PLAY, Component.text("Buat Lobby", NamedTextColor.GREEN, TextDecoration.BOLD)), null, 160,
                DialogAction.customClick((view, audience) -> {
                    List<Player> picked = new ArrayList<>();
                    for (int i = 0; i < others.size(); i++) {
                        if (Boolean.TRUE.equals(view.getBoolean("inv_" + i)) && others.get(i).isOnline()) {
                            picked.add(others.get(i));
                        }
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        GameSettings s = new GameSettings();
                        s.type = d.type;
                        s.testMode = d.test;
                        if (d.type == GameType.DEATHSWAP) {
                            s.swapIntervalSec = parseTime(d.swapText);
                        } else if (d.type == GameType.MACE) {
                            s.maceSec = parseTime(d.maceText);
                        } else {
                            s.itemIntervalSec = parseTime(d.itemText);
                            s.shrinkStartSec = parseTime(d.borderText);
                            s.speed = ShrinkSpeed.fromId(d.speed);
                            s.map = PofMap.fromId(d.map);
                            s.testPillars = parsePillars(d.testPillars);
                        }
                        s.rounds = Math.max(1, parseRounds(d.rounds));
                        s.drawEnabled = d.draw;
                        s.skipClinched = d.skip;
                        String err = plugin.manager().createLobby(host, picked, s);
                        if (err != null) openPlayers(plugin, host, d, err);
                    });
                }, once()));
        ActionButton back = ActionButton.create(lab(plugin, host, Hud.ICO_BACK, Component.text("Kembali", NamedTextColor.GRAY)), null, 120,
                DialogAction.customClick((view, audience) ->
                        Bukkit.getScheduler().runTask(plugin, () -> openSettings(plugin, host, d, null)), once()));

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title(plugin, host, "Pilih Pemain"))
                        .body(body).inputs(inputs).build())
                .type(DialogType.confirmation(create, back)));
        host.showDialog(dialog);
    }

    /** Panel buat yang lagi di lobby: ready / tolak / batalin, semua klik. */
    public static void openLobby(ZetMinigames plugin, Player p) {
        Lobby lobby = plugin.manager().lobbyOf(p.getUniqueId());
        if (lobby == null) { openMain(plugin, p); return; }
        boolean isHost = lobby.host.equals(p.getUniqueId());
        boolean isReady = lobby.ready.contains(p.getUniqueId());

        List<ActionButton> buttons = new ArrayList<>();
        if (!isReady) {
            buttons.add(btn(plugin, p, Hud.ICO_CHECK, "READY", NamedTextColor.GREEN, () -> plugin.manager().ready(p)));
        }
        if (isHost) {
            buttons.add(btn(plugin, p, Hud.ICO_CROSS, "Batal", NamedTextColor.RED, () -> plugin.manager().cancel(p)));
        } else {
            buttons.add(btn(plugin, p, Hud.ICO_CROSS, "Keluar", NamedTextColor.RED, () -> plugin.manager().leave(p)));
        }
        Component status = Component.text("Ready: " + lobby.ready.size() + "/" + lobby.invited.size());
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title(plugin, p, "Lobby " + lobby.settings.type.label))
                        .body(List.of(DialogBody.plainMessage(status)))
                        .build())
                .type(DialogType.multiAction(buttons, null, 1)));
        p.showDialog(dialog);
    }

    /** Panel buat yang lagi main: tombol menyerah. */
    public static void openInGame(ZetMinigames plugin, Player p) {
        List<ActionButton> buttons = new ArrayList<>();
        Match g = plugin.manager().gameOf(p.getUniqueId());
        if (g != null && g.isTestMode()) {
            buttons.add(btn(plugin, p, Hud.ICO_TROPHY, "Selesai (tes)",
                    NamedTextColor.GOLD, () -> g.testFinish(p)));
            buttons.add(btn(plugin, p, Hud.ICO_USERS, "Seri (tes)", NamedTextColor.LIGHT_PURPLE, () -> g.testDraw(p)));
        }
        buttons.add(btn(plugin, p, Hud.ICO_FLAG, "Menyerah", NamedTextColor.RED, () -> plugin.manager().leave(p)));
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title(plugin, p, "Game"))
                        .body(List.of(DialogBody.plainMessage(Component.text("Lagi main"))))
                        .build())
                .type(DialogType.multiAction(buttons, null, 1)));
        p.showDialog(dialog);
    }

    private static ActionButton btn(ZetMinigames plugin, Player p, String icon, String label, NamedTextColor color, Runnable action) {
        return ActionButton.create(lab(plugin, p, icon, Component.text(label, color)), null, 220,
                DialogAction.customClick((view, audience) ->
                                Bukkit.getScheduler().runTask(plugin, action),
                        ClickCallback.Options.builder().build()));
    }

    // ===================== PARSER =====================

    private static String nz(String v, String def) {
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    /** "30", "30s", "2m", "1m30s", "1j" -> detik. Return -1 kalau gak valid. */
    static int parseTime(String text) {
        if (text == null) return -1;
        String t = text.trim().toLowerCase().replace(",", ".");
        if (t.isEmpty()) return -1;
        Matcher m = Pattern.compile("(\\d+)\\s*([a-z]*)").matcher(t);
        long total = 0;
        int end = 0;
        boolean any = false;
        while (m.find()) {
            String between = t.substring(end, m.start()).trim();
            if (!between.isEmpty()) return -1;
            end = m.end();
            any = true;
            long n = Long.parseLong(m.group(1));
            String u = m.group(2);
            if (u.isEmpty() || u.startsWith("s") || u.startsWith("d")) total += n;
            else if (u.startsWith("m")) total += n * 60;
            else if (u.startsWith("j") || u.startsWith("h")) total += n * 3600;
            else return -1;
        }
        if (!any || !t.substring(end).trim().isEmpty()) return -1;
        if (total < 1) return -1;
        return (int) Math.min(total, 7200);
    }

    /** 1..10, atau -1 kalau gak valid. */
    private static int parseRounds(String t) {
        try {
            int v = Integer.parseInt(t.trim());
            return (v >= 1 && v <= 10) ? v : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static int parsePillars(String t) {
        try {
            return Math.max(2, Math.min(20, Integer.parseInt(t.trim())));
        } catch (Exception e) {
            return 4;
        }
    }
}
