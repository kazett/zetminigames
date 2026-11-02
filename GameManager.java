package id.zetminigames.core;

import id.zetminigames.battleroyale.BattleRoyaleGame;
import id.zetminigames.deathswap.DeathSwapGame;
import id.zetminigames.mace.MaceGame;
import id.zetminigames.pof.PofGame;
import id.zetminigames.ZetMinigames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class GameManager {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 10;
    private static final long LOBBY_TIMEOUT_TICKS = 20L * 120;
    private static final long BR_LOBBY_TIMEOUT_TICKS = 20L * 600;

    private final ZetMinigames plugin;
    private final Map<UUID, Lobby> lobbies = new HashMap<>();
    private final Map<UUID, Match> games = new HashMap<>();
    private final Set<Match> active = new HashSet<>();

    public GameManager(ZetMinigames plugin) {
        this.plugin = plugin;
    }

    public boolean busy(UUID id) { return lobbies.containsKey(id) || games.containsKey(id); }
    public Match gameOf(UUID id) { return games.get(id); }
    public Lobby lobbyOf(UUID id) { return lobbies.get(id); }

    // ===================== LOBBY =====================

    /** Return null kalau sukses, atau pesan error. */
    public String createLobby(Player host, List<Player> invited, GameSettings settings) {
        int total = invited.size() + 1;
        if (settings.testMode && total > 1) return "Mode tes cuma buat sendiri.";
        int min = settings.type.min;
        int max = settings.type.max;
        int lowest = settings.testMode ? settings.type.testMin : min;
        if (settings.type != GameType.BATTLEROYALE && (total < lowest || total > max)) {
            return "Pemain harus " + lowest + "-" + max;
        }
        if (busy(host.getUniqueId())) return "Lu lagi di lobby/game lain.";
        for (Player p : invited) {
            if (!p.isOnline()) return p.getName() + " udah offline.";
            if (busy(p.getUniqueId())) return p.getName() + " lagi di lobby/game lain.";
        }

        Lobby lobby = new Lobby(host.getUniqueId(), settings);
        lobby.invited.add(host.getUniqueId());
        lobby.ready.add(host.getUniqueId());
        lobbies.put(host.getUniqueId(), lobby);
        for (Player p : invited) {
            lobby.invited.add(p.getUniqueId());
            lobbies.put(p.getUniqueId(), lobby);
        }
        lobby.timeout = Bukkit.getScheduler().runTaskLater(plugin,
                () -> cancelLobby(lobby, "Lobby kadaluarsa."),
                settings.type == GameType.BATTLEROYALE ? BR_LOBBY_TIMEOUT_TICKS : LOBBY_TIMEOUT_TICKS);

        String detail = settings.type == GameType.BATTLEROYALE
                ? "Solo | " + settings.type.min + "-" + settings.type.max + " pemain"
                : settings.type == GameType.MACE ? "Timer " + GameSettings.fmt(settings.maceSec) + " | Ronde " + settings.rounds
                : settings.type == GameType.DEATHSWAP
                ? "TP " + GameSettings.fmt(settings.swapIntervalSec) + " | Ronde " + settings.rounds
                : "Item " + GameSettings.fmt(settings.itemIntervalSec)
                + " | Border " + GameSettings.fmt(settings.shrinkStartSec)
                + " | " + settings.speed.label
                + " | " + (settings.map == null ? "Random" : settings.map.label);
        Component info = Component.text(detail, NamedTextColor.GRAY);

        host.sendMessage(prefix().append(Component.text("Lobby dibuat.", NamedTextColor.YELLOW)));
        host.sendMessage(prefix().append(info));
        host.sendMessage(prefix().append(button("[BATAL]", NamedTextColor.RED, this::cancel, "Batal")));

        for (Player p : invited) {
            p.sendMessage(prefix().append(Component.text(host.getName() + " ngajak main " + settings.type.label, NamedTextColor.YELLOW)));
            p.sendMessage(prefix().append(info));
            p.sendMessage(prefix()
                    .append(button("[READY]", NamedTextColor.GREEN, this::ready, "Ready"))
                    .append(Component.text("  ", NamedTextColor.GRAY))
                    .append(button("[TOLAK]", NamedTextColor.RED, this::leave, "Tolak")));
        }
        if (settings.type != GameType.BATTLEROYALE && lobby.ready.size() == lobby.invited.size()) startGame(lobby);
        return null;
    }

    public void ready(Player p) {
        Lobby lobby = lobbies.get(p.getUniqueId());
        if (lobby == null) {
            p.sendMessage(prefix().append(Component.text("Gak ada lobby.", NamedTextColor.RED)));
            return;
        }
        if (!lobby.ready.add(p.getUniqueId())) {
            p.sendMessage(prefix().append(Component.text("Lu udah ready.", NamedTextColor.GRAY)));
            return;
        }
        lobbyMsg(lobby, Component.text(p.getName() + " ready (" + lobby.ready.size() + "/" + lobby.invited.size() + ")",
                NamedTextColor.GREEN));
        if (lobby.settings.type != GameType.BATTLEROYALE && lobby.ready.size() == lobby.invited.size()) startGame(lobby);
    }

    /** Tolak lobby / keluar lobby / menyerah di game. */
    public void leave(Player p) {
        Match g = games.get(p.getUniqueId());
        if (g != null) {
            g.forfeit(p);
            return;
        }
        Lobby lobby = lobbies.get(p.getUniqueId());
        if (lobby == null) {
            p.sendMessage(prefix().append(Component.text("Gak ada lobby/game.", NamedTextColor.RED)));
            return;
        }
        if (lobby.settings.type == GameType.BATTLEROYALE && !lobby.host.equals(p.getUniqueId())) {
            removeFromLobby(lobby, p.getUniqueId(), p.getName() + " keluar dari lobby.");
            p.sendMessage(prefix().append(Component.text("Keluar lobby.", NamedTextColor.GRAY)));
            return;
        }
        cancelLobby(lobby, p.getName() + " keluar, lobby batal.");
    }

    public void cancel(Player p) {
        Lobby lobby = lobbies.get(p.getUniqueId());
        if (lobby == null) {
            p.sendMessage(prefix().append(Component.text("Gak ada lobby.", NamedTextColor.RED)));
            return;
        }
        if (!lobby.host.equals(p.getUniqueId())) {
            p.sendMessage(prefix().append(Component.text("Cuma host.", NamedTextColor.RED)));
            return;
        }
        cancelLobby(lobby, "Lobby dibatal.");
    }

    private void cancelLobby(Lobby lobby, String reason) {
        if (lobby.timeout != null) lobby.timeout.cancel();
        Component c = prefix().append(Component.text(reason, NamedTextColor.RED));
        for (UUID id : lobby.invited) {
            lobbies.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(c);
        }
    }

    private void startGame(Lobby lobby) {
        if (lobby.timeout != null) lobby.timeout.cancel();
        List<Player> players = new ArrayList<>();
        for (UUID id : lobby.invited) {
            lobbies.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null) players.add(p);
        }
        if (players.size() < (lobby.settings.testMode ? lobby.settings.type.testMin : lobby.settings.type.min)) {
            for (Player p : players) {
                p.sendMessage(prefix().append(Component.text("Ada yang offline, batal.", NamedTextColor.RED)));
            }
            return;
        }
        Collections.shuffle(players);
        Match g = switch (lobby.settings.type) {
            case DEATHSWAP -> new DeathSwapGame(plugin, this, players, lobby.settings);
            case MACE -> new MaceGame(plugin, this, players, lobby.settings);
            case BATTLEROYALE -> new BattleRoyaleGame(plugin, this, players, lobby.settings);
            default -> new PofGame(plugin, this, players, lobby.settings);
        };
        active.add(g);
        for (Player p : players) games.put(p.getUniqueId(), g);
        if (!g.begin()) {
            for (Player p : players) {
                p.sendMessage(prefix().append(Component.text("Gagal bikin world.", NamedTextColor.RED)));
            }
            unregister(g);
        }
    }

    public String invite(Player host, List<Player> picked) {
        Lobby lobby = lobbies.get(host.getUniqueId());
        if (lobby == null || !lobby.host.equals(host.getUniqueId())) return "Lu bukan host lobby.";
        if (lobby.settings.testMode) return "Matiin MODE TES dulu buat ngundang pemain.";
        if (picked.isEmpty()) return "Pilih minimal 1 pemain.";
        int max = lobby.settings.type.max;
        if (lobby.invited.size() + picked.size() > max) return "Maksimal " + max + " pemain.";
        for (Player p : picked) {
            if (!p.isOnline()) return p.getName() + " udah offline.";
            if (busy(p.getUniqueId())) return p.getName() + " lagi di lobby/game lain.";
        }
        Component info = Component.text("Solo | " + lobby.settings.type.min + "-" + max + " pemain", NamedTextColor.GRAY);
        for (Player p : picked) {
            lobby.invited.add(p.getUniqueId());
            lobbies.put(p.getUniqueId(), lobby);
            p.sendMessage(prefix().append(Component.text(host.getName() + " ngajak main " + lobby.settings.type.label, NamedTextColor.YELLOW)));
            p.sendMessage(prefix().append(info));
            p.sendMessage(prefix()
                    .append(button("[READY]", NamedTextColor.GREEN, this::ready, "Ready"))
                    .append(Component.text("  ", NamedTextColor.GRAY))
                    .append(button("[TOLAK]", NamedTextColor.RED, this::leave, "Tolak")));
        }
        return null;
    }

    public String startLobby(Player host) {
        Lobby lobby = lobbies.get(host.getUniqueId());
        if (lobby == null || !lobby.host.equals(host.getUniqueId())) return "Lu bukan host lobby.";
        boolean test = lobby.settings.testMode;
        if (test && lobby.invited.size() > 1) return "Mode tes cuma buat sendiri. Matiin MODE TES atau batalin undangan.";
        int min = test ? lobby.settings.type.testMin : lobby.settings.type.min;
        if (lobby.ready.size() < min) return "Minimal " + min + " pemain READY (sekarang " + lobby.ready.size() + ").";
        for (UUID id : new ArrayList<>(lobby.invited)) {
            if (lobby.ready.contains(id)) continue;
            lobby.invited.remove(id);
            lobbies.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(prefix().append(Component.text("Game udah mulai.", NamedTextColor.GRAY)));
        }
        startGame(lobby);
        return null;
    }

    /** Host Battle Royale: nyalain / matiin MODE TES. Return null kalau sukses, atau pesan error. */
    public String toggleTestMode(Player host) {
        Lobby lobby = lobbies.get(host.getUniqueId());
        if (lobby == null || !lobby.host.equals(host.getUniqueId())) return "Lu bukan host lobby.";
        if (lobby.settings.type != GameType.BATTLEROYALE) return "Cuma buat Battle Royale.";
        if (!lobby.settings.testMode && lobby.invited.size() > 1) {
            return "Mode tes cuma buat sendiri. Keluarin pemain lain dulu (atau batalin lobby).";
        }
        lobby.settings.testMode = !lobby.settings.testMode;
        return null;
    }

    private void removeFromLobby(Lobby lobby, UUID id, String note) {
        lobby.invited.remove(id);
        lobby.ready.remove(id);
        lobbies.remove(id);
        Player host = Bukkit.getPlayer(lobby.host);
        if (host != null) host.sendMessage(prefix().append(Component.text(note, NamedTextColor.GRAY)));
    }

    // ===================== GAME =====================

    public void unregister(Match g) {
        for (UUID id : g.playerIds()) games.remove(id, g);
        active.remove(g);
    }

    public void forget(UUID id) {
        games.remove(id);
        Lobby l = lobbies.get(id);
        if (l != null) {
            Player p = Bukkit.getPlayer(id);
            String who = p != null ? p.getName() : "Pemain";
            if (l.settings.type == GameType.BATTLEROYALE && !l.host.equals(id)) {
                removeFromLobby(l, id, who + " keluar dari lobby.");
                return;
            }
            cancelLobby(l, who + " keluar, lobby batal.");
        }
    }

    public void shutdown() {
        for (Match g : new ArrayList<>(active)) g.forceShutdown();
        active.clear();
        games.clear();
        for (Lobby l : new ArrayList<>(lobbies.values())) {
            if (l.timeout != null) l.timeout.cancel();
        }
        lobbies.clear();
    }

    // ===================== UTIL =====================

    private void lobbyMsg(Lobby lobby, Component c) {
        for (UUID id : lobby.invited) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(prefix().append(c));
        }
    }

    private static Component prefix() {
        return Component.text("[MINIGAMES] ", NamedTextColor.GOLD);
    }

    /** Tombol klik di chat TANPA command (pakai click callback). */
    private Component button(String label, NamedTextColor color, Consumer<Player> action, String hover) {
        return Component.text(label, color, TextDecoration.BOLD)
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience instanceof Player p) {
                        Bukkit.getScheduler().runTask(plugin, () -> action.accept(p));
                    }
                }, ClickCallback.Options.builder()
                        .uses(ClickCallback.UNLIMITED_USES)
                        .lifetime(Duration.ofMinutes(3))
                        .build()))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    public static void deleteRecursive(File folder) {
        if (folder == null || !folder.exists()) return;
        try (Stream<java.nio.file.Path> walk = Files.walk(folder.toPath())) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
        }
    }
}
