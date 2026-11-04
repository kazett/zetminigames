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
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Panels {
    private Panels() {}

    private static final Key PANEL_FONT = Key.key("zetminigames", "panel");
    private static final int W = 320;
    private static final int HALF = 158;
    private static final TextColor LABEL = TextColor.color(0x9FD8FF);
    private static final TextColor HINT = TextColor.color(0x7FA8D8);
    private static final TextColor ACCENT = TextColor.color(0x5AD2FF);

    private static final class Draft {
        String itemText = "60s";
        String borderText = "5m";
        String speed = "SEDANG";
        boolean test = false;
        String testPillars = "4";
        String rounds = "1";
        boolean draw = false;
        boolean skip = true;
        boolean unique = true;
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

    private static Component title(ZetMinigames plugin, Player p, String t) {
        Component text = Component.text(t, NamedTextColor.GOLD, TextDecoration.BOLD);
        if (plugin.packReady(p)) {
            return Component.text(Hud.LOGO, NamedTextColor.WHITE).font(Hud.FONT)
                    .append(Component.text(" ")).append(text);
        }
        return text;
    }

    private static Component banner(ZetMinigames plugin, Player p, GameType type, String fallback) {
        if (!plugin.packReady(p)) {
            return Component.text(fallback, NamedTextColor.GOLD, TextDecoration.BOLD);
        }
        String glyph = switch (type) {
            case POF -> "\uE100";
            case DEATHSWAP -> "\uE101";
            case MACE -> "\uE102";
            case BATTLEROYALE -> "\uE103";
        };
        return Component.text(glyph, NamedTextColor.WHITE).font(PANEL_FONT);
    }

    private static DialogBody step(ZetMinigames plugin, Player p, int n, String label) {
        if (!plugin.packReady(p)) {
            return DialogBody.plainMessage(Component.text("Langkah " + n + "/3 - " + label, ACCENT, TextDecoration.BOLD), W);
        }
        String glyph = String.valueOf((char) (0xE110 + n - 1));
        return DialogBody.plainMessage(Component.text(glyph + "\n ", NamedTextColor.WHITE).font(PANEL_FONT), W);
    }

    private static Component lab(ZetMinigames plugin, Player p, String icon, Component text) {
        if (!plugin.packReady(p)) return text;
        return Component.empty().append(Component.text(icon, NamedTextColor.WHITE).font(Hud.FONT))
                .append(Component.text(" ")).append(text);
    }

    private static DialogInput field(String key, String label, String initial) {
        return DialogInput.text(key, Component.text(label, LABEL)).initial(initial).width(W).build();
    }

    private static DialogInput toggle(String key, String label, boolean on) {
        List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
        entries.add(SingleOptionDialogInput.OptionEntry.create("1",
                Component.text("ON", NamedTextColor.GREEN, TextDecoration.BOLD), on));
        entries.add(SingleOptionDialogInput.OptionEntry.create("0",
                Component.text("OFF", NamedTextColor.GRAY), !on));
        return DialogInput.singleOption(key, Component.text(label, NamedTextColor.WHITE), entries).width(W).build();
    }

    private static ClickCallback.Options once() {
        return ClickCallback.Options.builder().build();
    }

    public static void openMain(ZetMinigames plugin, Player p) {
        ActionButton pofBtn = ActionButton.create(
                lab(plugin, p, Hud.ICO_TROPHY, Component.text("Pillar of Fortune", NamedTextColor.GOLD, TextDecoration.BOLD)),
                Component.text("Pilar + item acak"),
                240,
                DialogAction.customClick((view, audience) ->
                        Bukkit.getScheduler().runTask(plugin, () -> openSettings(plugin, p, draftOf(GameType.POF), null)), once()));

        ActionButton dsBtn = ActionButton.create(
                lab(plugin, p, Hud.ICO_FLAG, Component.text("Deathswap", NamedTextColor.RED, TextDecoration.BOLD)),
                Component.text("Tukar posisi"),
                240,
                DialogAction.customClick((view, audience) ->
                        Bukkit.getScheduler().runTask(plugin, () -> openSettings(plugin, p, draftOf(GameType.DEATHSWAP), null)), once()));

        ActionButton maceBtn = ActionButton.create(
                lab(plugin, p, Hud.ICO_PLAY, Component.text("Mace Roulette", NamedTextColor.AQUA, TextDecoration.BOLD)),
                Component.text("Mace muter"),
                240,
                DialogAction.customClick((view, audience) ->
                        Bukkit.getScheduler().runTask(plugin, () -> openSettings(plugin, p, draftOf(GameType.MACE), null)), once()));

        ActionButton brBtn = ActionButton.create(
                lab(plugin, p, Hud.ICO_TROPHY, Component.text("Battle Royale", NamedTextColor.GREEN, TextDecoration.BOLD)),
                Component.text("Zona mengecil"),
                240,
                DialogAction.customClick((view, audience) ->
                        Bukkit.getScheduler().runTask(plugin, () -> createBrLobby(plugin, p)), once()));

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title(plugin, p, "GAMES"))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("Pilih game", NamedTextColor.GRAY))))
                        .build())
                .type(DialogType.multiAction(List.of(pofBtn, dsBtn, maceBtn, brBtn), null, 1)));
        p.showDialog(dialog);
    }

    private static void openSettings(ZetMinigames plugin, Player host, Draft d, String error) {
        boolean ds = d.type == GameType.DEATHSWAP;
        boolean mace = d.type == GameType.MACE;
        List<DialogBody> body = new ArrayList<>();
        body.add(step(plugin, host, 1, "SETTING"));
        body.add(DialogBody.plainMessage(Component.text("Format waktu: 30s, 2m", HINT), W));
        if (error != null) {
            body.add(DialogBody.plainMessage(Component.text(error, NamedTextColor.RED, TextDecoration.BOLD), W));
        }

        List<DialogInput> inputs = new ArrayList<>();
        if (ds) {
            inputs.add(field("swap", "Swap (min 10s)", d.swapText));
        } else if (!mace) {
            inputs.add(field("item", "Item tiap", d.itemText));
            inputs.add(field("border", "Border mulai", d.borderText));
            List<SingleOptionDialogInput.OptionEntry> speeds = new ArrayList<>();
            for (ShrinkSpeed s : ShrinkSpeed.values()) {
                speeds.add(SingleOptionDialogInput.OptionEntry.create(s.name(),
                        Component.text(s.label, ACCENT, TextDecoration.BOLD),
                        s.name().equals(d.speed)));
            }
            inputs.add(DialogInput.singleOption("speed", Component.text("Speed border", LABEL), speeds).width(W).build());
        } else {
            inputs.add(field("mace", "Timer (min 5s)", d.maceText));
        }
        inputs.add(field("rounds", "Ronde (1-10)", d.rounds));
        if (!ds && !mace) {
            List<SingleOptionDialogInput.OptionEntry> maps = new ArrayList<>();
            for (PofMap m : PofMap.values()) {
                maps.add(SingleOptionDialogInput.OptionEntry.create(m.name(),
                        Component.text(m.label, m.color, TextDecoration.BOLD), m.name().equals(d.map)));
            }
            maps.add(SingleOptionDialogInput.OptionEntry.create("RANDOM",
                    Component.text("Random", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD), "RANDOM".equals(d.map)));
            inputs.add(DialogInput.singleOption("map", Component.text("Map", LABEL), maps).width(W).build());
        }
        if (ds) {
            inputs.add(toggle("unique", "Gak boleh mati cara sama", d.unique));
        }
        inputs.add(toggle("draw", "Boleh seri", d.draw));
        inputs.add(toggle("skip", "Stop kalau pasti menang", d.skip));
        inputs.add(toggle("test", "Mode tes", d.test));
        if (!ds && !mace) {
            inputs.add(field("pillars", "Pilar tes (2-20)", d.testPillars));
        }

        ActionButton next = ActionButton.create(
                lab(plugin, host, Hud.ICO_USERS, Component.text("Lanjut", NamedTextColor.GREEN, TextDecoration.BOLD)), null, HALF,
                DialogAction.customClick((view, audience) -> {
                    d.rounds = nz(view.getText("rounds"), "1");
                    d.draw = "1".equals(view.getText("draw"));
                    d.skip = "1".equals(view.getText("skip"));
                    d.test = "1".equals(view.getText("test"));
                    if (ds) {
                        d.unique = "1".equals(view.getText("unique"));
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
                            if (swap < GameSettings.MIN_SWAP_SEC) { openSettings(plugin, host, d, "Swap min 10s"); return; }
                        } else if (mace) {
                            if (parseTime(d.maceText) < 5) { openSettings(plugin, host, d, "Timer min 5s"); return; }
                        } else {
                            int item = parseTime(d.itemText);
                            int border = parseTime(d.borderText);
                            if (item < 0) { openSettings(plugin, host, d, "Item salah"); return; }
                            if (border < 0) { openSettings(plugin, host, d, "Border salah"); return; }
                        }
                        if (parseRounds(d.rounds) < 0) { openSettings(plugin, host, d, "Ronde harus 1-10"); return; }
                        openPlayers(plugin, host, d, null);
                    });
                }, once()));
        ActionButton cancel = ActionButton.create(lab(plugin, host, Hud.ICO_CROSS, Component.text("Batal", NamedTextColor.RED)), null, HALF, null);

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(banner(plugin, host, d.type, d.type.label))
                        .body(body).inputs(inputs).build())
                .type(DialogType.confirmation(next, cancel)));
        host.showDialog(dialog);
    }

    private static void openPlayers(ZetMinigames plugin, Player host, Draft d, String error) {
        List<Player> others = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(host) || plugin.manager().busy(o.getUniqueId())) continue;
            others.add(o);
            if (others.size() >= 30) break;
        }

        List<DialogBody> body = new ArrayList<>();
        body.add(step(plugin, host, 2, "PEMAIN"));
        body.add(DialogBody.plainMessage(Component.text(
                d.test ? "Mode tes: gak usah pilih pemain"
                        : "Pilih " + (d.type.min - 1) + "-" + (d.type.max - 1) + " pemain",
                HINT), W));
        if (others.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("Gak ada yang online", NamedTextColor.DARK_GRAY), W));
        }
        if (error != null) {
            body.add(DialogBody.plainMessage(Component.text(error, NamedTextColor.RED, TextDecoration.BOLD), W));
        }

        List<DialogInput> inputs = new ArrayList<>();
        for (int i = 0; i < others.size(); i++) {
            inputs.add(DialogInput.bool("inv_" + i, Component.text(others.get(i).getName(), NamedTextColor.WHITE))
                    .initial(false).build());
        }

        ActionButton create = ActionButton.create(
                lab(plugin, host, Hud.ICO_PLAY, Component.text("Buat Lobby", NamedTextColor.GREEN, TextDecoration.BOLD)), null, HALF,
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
                        s.uniqueDeath = d.unique;
                        String err = plugin.manager().createLobby(host, picked, s);
                        if (err != null) openPlayers(plugin, host, d, err);
                    });
                }, once()));
        ActionButton back = ActionButton.create(lab(plugin, host, Hud.ICO_BACK, Component.text("Kembali", NamedTextColor.GRAY)), null, HALF,
                DialogAction.customClick((view, audience) ->
                        Bukkit.getScheduler().runTask(plugin, () -> openSettings(plugin, host, d, null)), once()));

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(banner(plugin, host, d.type, "Pilih Pemain"))
                        .body(body).inputs(inputs).build())
                .type(DialogType.confirmation(create, back)));
        host.showDialog(dialog);
    }

    public static void openLobby(ZetMinigames plugin, Player p) {
        Lobby lobby = plugin.manager().lobbyOf(p.getUniqueId());
        if (lobby == null) { openMain(plugin, p); return; }
        boolean isHost = lobby.host.equals(p.getUniqueId());
        boolean isReady = lobby.ready.contains(p.getUniqueId());
        if (isHost && lobby.settings.type == GameType.BATTLEROYALE) { openBrHost(plugin, p, null); return; }

        List<ActionButton> buttons = new ArrayList<>();
        if (!isReady) {
            buttons.add(btn(plugin, p, Hud.ICO_CHECK, "READY", NamedTextColor.GREEN, () -> plugin.manager().ready(p)));
        }
        if (isHost) {
            buttons.add(btn(plugin, p, Hud.ICO_CROSS, "Batal", NamedTextColor.RED, () -> plugin.manager().cancel(p)));
        } else {
            buttons.add(btn(plugin, p, Hud.ICO_CROSS, "Keluar", NamedTextColor.RED, () -> plugin.manager().leave(p)));
        }
        Component status = Component.text("Ready: " + lobby.ready.size() + "/" + lobby.invited.size(), ACCENT, TextDecoration.BOLD);
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(banner(plugin, p, lobby.settings.type, "Lobby " + lobby.settings.type.label))
                        .body(List.of(step(plugin, p, 3, "LOBBY"), DialogBody.plainMessage(status, W)))
                        .build())
                .type(DialogType.multiAction(buttons, null, 1)));
        p.showDialog(dialog);
    }

    private static void createBrLobby(ZetMinigames plugin, Player host) {
        GameSettings s = new GameSettings();
        s.type = GameType.BATTLEROYALE;
        String err = plugin.manager().createLobby(host, new ArrayList<>(), s);
        if (err != null) {
            host.sendMessage(Component.text(err, NamedTextColor.RED));
            return;
        }
        openBrHost(plugin, host, null);
    }

    private static void openBrHost(ZetMinigames plugin, Player host, String error) {
        Lobby lobby = plugin.manager().lobbyOf(host.getUniqueId());
        if (lobby == null) { openMain(plugin, host); return; }
        GameType type = lobby.settings.type;
        List<String> readyNames = new ArrayList<>();
        List<String> waitNames = new ArrayList<>();
        for (UUID id : lobby.invited) {
            Player o = Bukkit.getPlayer(id);
            String name = o != null ? o.getName() : "?";
            if (lobby.ready.contains(id)) readyNames.add(name); else waitNames.add(name);
        }

        List<DialogBody> body = new ArrayList<>();
        boolean test = lobby.settings.testMode;
        body.add(DialogBody.plainMessage(Component.text(
                test ? "READY " + lobby.ready.size() + "/" + type.max + " (tes)"
                        : "READY " + lobby.ready.size() + "/" + type.max + " (min " + type.min + ")",
                ACCENT, TextDecoration.BOLD), W));
        if (test) {
            body.add(DialogBody.plainMessage(Component.text(
                    "Mode tes: tutup game lewat /panel > Selesai.",
                    NamedTextColor.GOLD), W));
        }
        body.add(DialogBody.plainMessage(Component.text(String.join(", ", readyNames), NamedTextColor.GREEN), W));
        if (!waitNames.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("Menunggu: " + String.join(", ", waitNames), HINT), W));
        }
        if (error != null) {
            body.add(DialogBody.plainMessage(Component.text(error, NamedTextColor.RED, TextDecoration.BOLD), W));
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(btn(plugin, host, Hud.ICO_PLAY, "Start", NamedTextColor.GREEN, () -> {
            String err = plugin.manager().startLobby(host);
            if (err != null) openBrHost(plugin, host, err);
        }));
        buttons.add(btn(plugin, host, Hud.ICO_FLAG, test ? "MODE TES: ON" : "MODE TES: OFF",
                test ? NamedTextColor.GOLD : NamedTextColor.GRAY, () -> {
                    String err = plugin.manager().toggleTestMode(host);
                    openBrHost(plugin, host, err);
                }));
        buttons.add(btn(plugin, host, Hud.ICO_USERS, "Undang", NamedTextColor.WHITE, () -> openBrInvite(plugin, host, null)));
        buttons.add(btn(plugin, host, Hud.ICO_CHECK, "Refresh", NamedTextColor.AQUA, () -> openBrHost(plugin, host, null)));
        buttons.add(btn(plugin, host, Hud.ICO_CROSS, "Batal", NamedTextColor.RED, () -> plugin.manager().cancel(host)));

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(banner(plugin, host, type, "Lobby " + type.label))
                        .body(body).build())
                .type(DialogType.multiAction(buttons, null, 1)));
        host.showDialog(dialog);
    }

    private static void openBrInvite(ZetMinigames plugin, Player host, String error) {
        Lobby lobby = plugin.manager().lobbyOf(host.getUniqueId());
        if (lobby == null) { openMain(plugin, host); return; }
        List<Player> others = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(host) || plugin.manager().busy(o.getUniqueId())) continue;
            others.add(o);
            if (others.size() >= lobby.settings.type.max - 1) break;
        }

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("Pilih pemain", HINT), W));
        if (others.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("Gak ada yang online", NamedTextColor.DARK_GRAY), W));
        }
        if (error != null) {
            body.add(DialogBody.plainMessage(Component.text(error, NamedTextColor.RED, TextDecoration.BOLD), W));
        }

        List<DialogInput> inputs = new ArrayList<>();
        for (int i = 0; i < others.size(); i++) {
            inputs.add(DialogInput.bool("inv_" + i, Component.text(others.get(i).getName(), NamedTextColor.WHITE))
                    .initial(false).build());
        }

        ActionButton send = ActionButton.create(
                lab(plugin, host, Hud.ICO_PLAY, Component.text("Undang", NamedTextColor.GREEN, TextDecoration.BOLD)), null, HALF,
                DialogAction.customClick((view, audience) -> {
                    List<Player> picked = new ArrayList<>();
                    for (int i = 0; i < others.size(); i++) {
                        if (Boolean.TRUE.equals(view.getBoolean("inv_" + i)) && others.get(i).isOnline()) {
                            picked.add(others.get(i));
                        }
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String err = plugin.manager().invite(host, picked);
                        if (err != null) openBrInvite(plugin, host, err);
                        else openBrHost(plugin, host, null);
                    });
                }, once()));
        ActionButton back = ActionButton.create(lab(plugin, host, Hud.ICO_BACK, Component.text("Kembali", NamedTextColor.GRAY)), null, HALF,
                DialogAction.customClick((view, audience) ->
                        Bukkit.getScheduler().runTask(plugin, () -> openBrHost(plugin, host, null)), once()));

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(banner(plugin, host, lobby.settings.type, "Undang"))
                        .body(body).inputs(inputs).build())
                .type(DialogType.confirmation(send, back)));
        host.showDialog(dialog);
    }

    public static void openInGame(ZetMinigames plugin, Player p) {
        List<ActionButton> buttons = new ArrayList<>();
        Match g = plugin.manager().gameOf(p.getUniqueId());
        if (g != null && g.isTestMode()) {
            buttons.add(btn(plugin, p, Hud.ICO_TROPHY, "Selesai (tes)",
                    NamedTextColor.GOLD, () -> g.testFinish(p)));
            if (g.hasTestDraw()) {
                buttons.add(btn(plugin, p, Hud.ICO_USERS, "Seri (tes)", NamedTextColor.LIGHT_PURPLE, () -> g.testDraw(p)));
            }
        }
        buttons.add(btn(plugin, p, Hud.ICO_FLAG, "Menyerah", NamedTextColor.RED, () -> plugin.manager().leave(p)));
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title(plugin, p, "Game"))
                        .body(List.of(DialogBody.plainMessage(g != null && g.isTestMode()
                                ? Component.text("Mode tes: tekan Selesai buat nutup game.", NamedTextColor.GOLD)
                                : Component.text("Lagi main"))))
                        .build())
                .type(DialogType.multiAction(buttons, null, 1)));
        p.showDialog(dialog);
    }

    private static ActionButton btn(ZetMinigames plugin, Player p, String icon, String label, NamedTextColor color, Runnable action) {
        return ActionButton.create(lab(plugin, p, icon, Component.text(label, color)), null, W,
                DialogAction.customClick((view, audience) ->
                                Bukkit.getScheduler().runTask(plugin, action),
                        ClickCallback.Options.builder().build()));
    }

    private static String nz(String v, String def) {
        return (v == null || v.isBlank()) ? def : v.trim();
    }

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
