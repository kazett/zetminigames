package id.zetminigames.deathswap;

import id.zetminigames.hud.CustomTitle;
import id.zetminigames.core.GameManager;
import id.zetminigames.core.GameSettings;
import id.zetminigames.hud.Hud;
import id.zetminigames.core.Match;
import id.zetminigames.core.PlayerSnapshot;
import id.zetminigames.hud.WinScreen;
import id.zetminigames.ZetMinigames;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class DeathSwapGame implements Match {

    @Override
    public boolean allowDrop() {
        return true;
    }
    private enum State { LOADING, COUNTDOWN, RUNNING, ENDED }

    private static final int COUNTDOWN_SEC = 5;
    /** Waktu siap-siap (detik) sebelum countdown 5..1: pemain udah di spawn tapi belum bisa gerak (world sempat ke-render). */
    private static final int READY_SEC = 10;
    private static final int WARN_SEC = 5;
    private static final double SPREAD_BASE = 120.0;
    private static final double SPREAD_PER_PLAYER = 30.0;
    private static final int SPAWN_TRIES = 40;
    /** Jarak TP ronde berikutnya dari titik spawn pemain di ronde sebelumnya (block). */
    private static final double MOVE_MIN = 500.0;
    private static final double MOVE_MAX = 1000.0;
    /** Jarak minimal antar pemain di ronde yang sama (block). */
    private static final double MIN_GAP = 120.0;
    /** Cek air: 3x3 titik sampel, jarak antar titik SAMPLE_STEP block. Maks MAX_WATER_SAMPLES titik boleh air/lava. */
    private static final int SAMPLE_STEP = 24;
    private static final int MAX_WATER_SAMPLES = 2;

    private final ZetMinigames plugin;
    private final GameManager manager;
    private final GameSettings settings;

    private final List<UUID> order = new ArrayList<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Map<UUID, PlayerSnapshot> snaps = new HashMap<>();
    private final Map<UUID, Location> deathLocs = new HashMap<>();
    private final List<UUID> roundOrder = new ArrayList<>();
    private final Map<UUID, Integer> wins = new HashMap<>();
    private final Map<UUID, Component> heads = new HashMap<>();
    private int roundNo = 0;
    private boolean bonus = false;
    private BukkitTask nextTask;
    /** Titik spawn ronde ini. Dipakai mode tes solo buat pindah-pindah. */
    private List<Location> spawns = new ArrayList<>();
    private int soloIdx = 0;
    /** Titik spawn terakhir tiap pemain (buat hitung jarak TP ronde berikutnya). */
    private final Map<UUID, Location> lastSpawn = new HashMap<>();

    // ===== Aturan anti-meta: 1 pemain gak boleh mati karena jebakan dengan cara yang sama dua kali =====
    /** Default: kematian dihitung "kena jebakan" kalau terjadi maks sekian detik setelah TP (config deathswap.trap-window-sec). */
    private static final int DEFAULT_TRAP_WINDOW_SEC = 30;
    /** Default: dan maks sekian block (horizontal) dari titik tujuan TP (config deathswap.trap-radius). */
    private static final double DEFAULT_TRAP_RADIUS = 40.0;
    /** Cara mati (kategori) yang SUDAH TERCATAT per pemain dari ronde-ronde sebelumnya (cuma kematian kena jebakan). */
    private final Map<UUID, Set<String>> trapDeaths = new HashMap<>();
    /** Cara mati kena jebakan di ronde yang lagi jalan. Baru dicatat permanen kalau ronde dihitung (gak di-void). */
    private final Map<UUID, String> roundTrapCause = new HashMap<>();
    /** Waktu (ms) dan titik tujuan TP terakhir tiap pemain di ronde ini. */
    private final Map<UUID, Long> swappedAt = new HashMap<>();
    private final Map<UUID, Location> swapSpot = new HashMap<>();
    /** true = ronde barusan di-void (seri karena cara mati sama), nextRound() ngulang ronde yang sama. */
    private boolean replay = false;

    private World world;
    private Location spectatorLoc;
    private State state = State.LOADING;
    private BukkitTask countdownTask, tickTask, endTask, hudTask;
    private int nextSwap;
    /** Detik sampai game mulai (siap-siap + countdown), 0 kalau udah jalan. Buat HUD. */
    private int readyLeft;
    /** Waktu mulai fase jalan (ms), buat jam game di HUD. */
    private long runStart;
    private long periodStart;
    private BossBar bossBar;

    public DeathSwapGame(ZetMinigames plugin, GameManager manager, List<Player> players, GameSettings settings) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
        for (Player p : players) {
            order.add(p.getUniqueId());
            names.put(p.getUniqueId(), p.getName());
        }
    }

    @Override
    public List<UUID> playerIds() { return order; }

    @Override
    public boolean isTestMode() { return settings.testMode; }

    @Override
    public int rounds() { return settings.rounds; }

    @Override
    public void testFinish(Player p) {
        if (!settings.testMode || state != State.RUNNING) return;
        UUID id = p.getUniqueId();
        UUID w = alive.contains(id) ? id : (alive.isEmpty() ? null : alive.iterator().next());
        finish(w);
    }

    @Override
    public void testDraw(Player p) {
        if (!settings.testMode || state != State.RUNNING) return;
        state = State.ENDED;
        cancelTasks();
        hideHud();
        for (Player q : participants()) q.sendActionBar(Component.empty());
        showDraw(List.of(p.getUniqueId()));
    }

    @Override
    public boolean begin() {
        roundNo = 1;
        bonus = false;
        roundOrder.clear();
        roundOrder.addAll(order);
        for (Player p : participants()) heads.put(p.getUniqueId(), Hud.head(p));
        bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        return startRound();
    }

    private boolean startRound() {
        // World dibuat sekali di ronde 1, ronde berikutnya pakai world yang sama (cuma TP ke titik random baru)
        final World created;
        if (world != null) {
            created = world;
        } else {
            WorldCreator wc = new WorldCreator("pof_ds_" + Long.toString(System.currentTimeMillis(), 36));
            World c = wc.createWorld();
            if (c == null) return false;
            world = c;
            world.setDifficulty(Difficulty.NORMAL);
            world.setTime(1000L);
            world.setStorm(false);
            created = c;
        }
        final World prev = null;

        alive.clear();
        deathLocs.clear();
        swappedAt.clear();
        swapSpot.clear();
        roundTrapCause.clear();
        state = State.LOADING;

        for (UUID id : roundOrder) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            snaps.computeIfAbsent(id, k -> new PlayerSnapshot(p));
            alive.add(id);
        }
        for (UUID id : order) {
            if (roundOrder.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            snaps.computeIfAbsent(id, k -> new PlayerSnapshot(p));
        }

        int n = Math.max(roundOrder.size(), settings.testMode ? 2 : 0);
        // titik acuan per pemain = spawn ronde sebelumnya (null kalau ronde 1)
        List<Location> prevs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Location pv = null;
            if (!this.spawns.isEmpty()) {
                if (i < roundOrder.size()) pv = lastSpawn.get(roundOrder.get(i));
                if (pv == null) pv = this.spawns.get(i % this.spawns.size());
            }
            prevs.add(pv);
        }
        double radius = SPREAD_BASE + SPREAD_PER_PLAYER * n;
        double offset = ThreadLocalRandom.current().nextDouble() * 2.0 * Math.PI;
        List<Location> picked = new ArrayList<>();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final int total = n;
            chain = chain.thenCompose(v -> findSpawn(created, prevs.get(idx),
                    offset + 2.0 * Math.PI * idx / total, radius, picked, SPAWN_TRIES, null, Integer.MAX_VALUE)
                    .thenAccept(picked::add));
        }
        hudTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateHud, 5L, 5L);
        chain.whenComplete((v, t) ->
                Bukkit.getScheduler().runTask(plugin, () -> enterRound(created, prev, picked)));
        return true;
    }

    /**
     * Cari 1 titik spawn. Ronde 1: cincin di sekitar 0,0. Ronde berikutnya: 500-1000 block dari titik
     * spawn pemain ini sebelumnya. Titik ditolak kalau kebanyakan air atau terlalu dekat pemain lain.
     * Kalau percobaan habis, pakai kandidat terbaik (air paling sedikit).
     */
    private CompletableFuture<Location> findSpawn(World w, Location prev, double angle, double radius,
                                                  List<Location> taken, int tries, Location best, int bestPenalty) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double cx, cz;
        if (prev == null) {
            double a = angle + (rng.nextDouble() - 0.5) * 0.5;
            double r = radius + (rng.nextDouble() - 0.5) * 80.0;
            cx = r * Math.cos(a);
            cz = r * Math.sin(a);
        } else {
            double a = rng.nextDouble() * 2.0 * Math.PI;
            double d = MOVE_MIN + rng.nextDouble() * (MOVE_MAX - MOVE_MIN);
            cx = prev.getX() + d * Math.cos(a);
            cz = prev.getZ() + d * Math.sin(a);
        }
        final int x = (int) Math.round(cx);
        final int z = (int) Math.round(cz);

        boolean gapOk = true;
        for (Location t : taken) {
            double dx = t.getX() - x, dz = t.getZ() - z;
            if (dx * dx + dz * dz < MIN_GAP * MIN_GAP) { gapOk = false; break; }
        }
        final boolean gapOkF = gapOk;

        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                loads.add(w.getChunkAtAsync((x + dx * SAMPLE_STEP) >> 4, (z + dz * SAMPLE_STEP) >> 4));
            }
        }
        return CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).thenCompose(v -> {
            int water = 0;
            boolean centerWet = false;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int sx = x + dx * SAMPLE_STEP, sz = z + dz * SAMPLE_STEP;
                    int sy = w.getHighestBlockYAt(sx, sz);
                    if (w.getBlockAt(sx, sy, sz).isLiquid()) {
                        water++;
                        if (dx == 0 && dz == 0) centerWet = true;
                    }
                }
            }
            int y = w.getHighestBlockYAt(x, z);
            Location cand = new Location(w, x + 0.5, y + 1.0, z + 0.5);
            boolean ok = !centerWet && water <= MAX_WATER_SAMPLES && gapOkF;
            if (ok) return CompletableFuture.completedFuture(cand);
            int penalty = water + (centerWet ? 20 : 0) + (gapOkF ? 0 : 20);
            Location nb = best;
            int np = bestPenalty;
            if (penalty < bestPenalty) { nb = cand; np = penalty; }
            if (tries <= 0) return CompletableFuture.completedFuture(nb != null ? nb : cand);
            return findSpawn(w, prev, angle, radius, taken, tries - 1, nb, np);
        });
    }

    private void enterRound(World w, World prev, List<Location> picked) {
        if (state != State.LOADING || world != w) return;
        List<Location> spawns = new ArrayList<>(picked);
        int need = Math.max(roundOrder.size(), settings.testMode ? 2 : 0);
        while (spawns.size() < need) spawns.add(w.getSpawnLocation());
        spectatorLoc = spawns.get(0);
        this.spawns = spawns;
        soloIdx = 0;
        for (int i = 0; i < roundOrder.size() && i < spawns.size(); i++) lastSpawn.put(roundOrder.get(i), spawns.get(i));

        for (int i = 0; i < roundOrder.size(); i++) {
            Player p = Bukkit.getPlayer(roundOrder.get(i));
            if (p == null) continue;
            prepare(p);
            p.teleport(spawns.get(i));
        }
        for (UUID id : order) {
            if (roundOrder.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.getInventory().clear();
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(spectatorLoc);
        }

        for (Player p : participants()) {
            if (plugin.packReady(p)) p.showBossBar(bossBar);
        }
        nextSwap = settings.swapIntervalSec;
        periodStart = System.currentTimeMillis();
        updateHud();

        state = State.COUNTDOWN;
        readyLeft = READY_SEC + COUNTDOWN_SEC;
        updateHud();
        countdownTask = new org.bukkit.scheduler.BukkitRunnable() {
            int c = COUNTDOWN_SEC;
            int wait = READY_SEC;

            @Override
            public void run() {
                if (state == State.ENDED) { cancel(); return; }
                if (wait > 0) {
                    readyLeft = wait + c;
                    if (wait == READY_SEC) {   // banner BERSIAP custom, tampil selama masa siap-siap
                        for (Player p : participants()) {
                            CustomTitle.show(plugin, p, CustomTitle.banner(CustomTitle.BANNER_READY),
                                    Component.text("BERSIAP", NamedTextColor.AQUA, TextDecoration.BOLD),
                                    Component.text("Tunggu semua", NamedTextColor.GRAY),
                                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(READY_SEC), Duration.ZERO));
                        }
                    }
                    updateHud();
                    wait--;
                    return;
                }
                if (c > 0) {
                    readyLeft = c;
                    updateHud();
                    for (Player p : participants()) {
                        CustomTitle.show(plugin, p, CustomTitle.count(c),
                                Component.text(String.valueOf(c), NamedTextColor.YELLOW, TextDecoration.BOLD),
                                Component.text(roundSubtitle(), NamedTextColor.GRAY),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100)));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    }
                    c--;
                } else {
                    cancel();
                    startRunning();
                }
            }
        }.runTaskTimer(plugin, 10L, 20L);

        if (prev != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> deleteWorld(prev), 20L);
        }
    }

    private String roundLabel() {
        if (bonus) return "BONUS";
        return settings.rounds > 1 ? "R " + roundNo + "/" + settings.rounds : "";
    }

    private String roundSubtitle() {
        if (bonus) return "BONUS MATCH";
        return settings.rounds > 1 ? "Ronde " + roundNo + "/" + settings.rounds : "Siap-siap!";
    }

    private void prepare(Player p) {
        p.getInventory().clear();
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setLevel(0);
        p.setExp(0f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) {
            p.removePotionEffect(e.getType());
        }
    }

    private void startRunning() {
        state = State.RUNNING;
        runStart = System.currentTimeMillis();
        readyLeft = 0;
        nextSwap = settings.swapIntervalSec;
        periodStart = System.currentTimeMillis();
        for (Player p : participants()) {
            CustomTitle.show(plugin, p, CustomTitle.banner(CustomTitle.BANNER_GO),
                    Component.text("MULAI!", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("Jebak lawan", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ofMillis(200)));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1f);
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        if (state != State.RUNNING) return;
        nextSwap--;
        if (nextSwap <= 0) {
            swap();
            nextSwap = settings.swapIntervalSec;
            periodStart = System.currentTimeMillis();
        } else if (nextSwap <= WARN_SEC) {
            warn(nextSwap);
        }
    }

    private void warn(int seconds) {
        for (Player p : participants()) {
            CustomTitle.show(plugin, p, CustomTitle.tpWarn(seconds),
                    Component.empty(),
                    Component.text("TP dalam " + seconds, NamedTextColor.RED, TextDecoration.BOLD),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100)));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f);
        }
    }

    private void swap() {
        List<UUID> ids = new ArrayList<>();
        Map<UUID, Location> spots = new HashMap<>();
        for (UUID id : alive) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            ids.add(id);
            spots.put(id, p.getLocation());
        }
        if (ids.size() == 1 && settings.testMode && spawns.size() > 1) {
            // mode tes solo: tukar ke titik lain (pura-pura ada lawan)
            soloIdx = (soloIdx + 1) % spawns.size();
            Player p = Bukkit.getPlayer(ids.get(0));
            if (p != null) {
                Location dest = spawns.get(soloIdx).clone();
                dest.setYaw(p.getLocation().getYaw());
                dest.setPitch(p.getLocation().getPitch());
                p.teleport(dest);
                markSwap(p.getUniqueId(), dest);
            }
        } else if (ids.size() < 2) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (UUID id : ids) {
            if (ids.size() < 2) break;
            UUID target;
            do {
                target = ids.get(rng.nextInt(ids.size()));
            } while (target.equals(id));
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            Location dest = spots.get(target).clone();
            dest.setYaw(p.getLocation().getYaw());
            dest.setPitch(p.getLocation().getPitch());
            p.teleport(dest);
            markSwap(id, dest);
        }
        for (Player p : participants()) {
            CustomTitle.show(plugin, p, CustomTitle.banner(CustomTitle.BANNER_SWAP),
                    Component.empty(),
                    Component.text("TUKAR POSISI!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ofMillis(200)));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        }
    }


    private void updateHud() {
        if (bossBar == null) return;
        int n = roundOrder.size();
        List<Component> hs = new ArrayList<>();
        boolean[] al = new boolean[n];
        for (int i = 0; i < n; i++) {
            UUID id = roundOrder.get(i);
            Component h = heads.get(id);
            hs.add(h != null ? h : Component.text("?"));
            al[i] = alive.contains(id);
        }
        double frac = 0.0;
        if (readyLeft > 0) {
            frac = readyLeft / (double) (READY_SEC + COUNTDOWN_SEC);
        } else if (state == State.RUNNING) {
            frac = (System.currentTimeMillis() - periodStart) / (settings.swapIntervalSec * 1000.0);
        }
        bossBar.name(Hud.buildSwap(hs, al, alive.size(), n, Math.max(0, nextSwap), frac,
                plugin.headAdvance(), plugin.slotWidth(), roundLabel(),
                readyLeft > 0 ? "MULAI " + readyLeft + "s" : null,
                state == State.RUNNING ? (int) ((System.currentTimeMillis() - runStart) / 1000) : 0));
    }

    private void hideHud() {
        if (bossBar == null) return;
        for (Player p : participants()) p.hideBossBar(bossBar);
    }

    public void eliminate(Player p, String cause) {
        UUID id = p.getUniqueId();
        if (state != State.RUNNING || !alive.remove(id)) return;

        Component line = Component.text(names.get(id), NamedTextColor.RED)
                .append(Component.text(" " + cause, NamedTextColor.GRAY))
                .append(Component.text("  (" + alive.size() + " sisa)", NamedTextColor.DARK_GRAY));
        msg(line);

        Location at = p.getLocation();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || p.isDead()) return;
            for (ItemStack it : p.getInventory().getContents()) {
                if (it != null && !it.getType().isAir()) at.getWorld().dropItemNaturally(at, it);
            }
            p.getInventory().clear();
            p.setHealth(20.0);
            p.setFireTicks(0);
            p.setFallDistance(0f);
            p.setGameMode(GameMode.SPECTATOR);
            CustomTitle.show(plugin, p, CustomTitle.banner(CustomTitle.BANNER_ELIM),
                    Component.text("TERELIMINASI", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Nonton aja", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(3000), Duration.ofMillis(500)));
        });
        updateHud();
        checkWin();
    }

    private void checkWin() {
        if (state == State.ENDED) return;
        if (alive.size() <= (settings.testMode ? 0 : 1)) {
            UUID w = (state == State.RUNNING && !alive.isEmpty()) ? alive.iterator().next() : null;
            finish(w);
        }
    }

    private void finish(UUID winner) {
        if (state == State.ENDED) return;
        state = State.ENDED;
        cancelTasks();
        hideHud();
        for (Player p : participants()) p.sendActionBar(Component.empty());
        commitTrapCauses();   // ronde dihitung: cara mati kena jebakan dicatat permanen

        String doneLabel = bonus ? "BONUS MATCH"
                : "RONDE " + roundNo + (settings.rounds > 1 ? "/" + settings.rounds : "");

        if (bonus) {
            if (winner != null) { showChampion(winner); return; }
            showRound(doneLabel, null, "Seri, diulang.");
            scheduleNext();
            return;
        }

        if (winner != null) wins.merge(winner, 1, Integer::sum);
        int remaining = settings.rounds - roundNo;
        boolean soloLeft = !settings.testMode && order.size() < 2;
        boolean over = remaining <= 0 || soloLeft || (settings.skipClinched && clinched(remaining));
        if (!over) {
            showRound(doneLabel, winner, null);
            scheduleNext();
            return;
        }
        if (soloLeft) {
            if (order.isEmpty()) showNone(); else showChampion(order.get(0));
            return;
        }
        List<UUID> top = topPlayers();
        if (top.isEmpty()) { showNone(); return; }
        if (top.size() == 1) { showChampion(top.get(0)); return; }
        if (settings.drawEnabled) { showDraw(top); return; }

        bonus = true;
        roundOrder.clear();
        roundOrder.addAll(top);
        showRound(doneLabel, winner, "Seri: " + joinNames(top) + ". BONUS MATCH!");
        scheduleNext();
    }

    private boolean clinched(int remaining) {
        int lead = 0, second = 0;
        for (UUID id : order) {
            int w = wins.getOrDefault(id, 0);
            if (w > lead) { second = lead; lead = w; }
            else if (w > second) second = w;
        }
        return lead > second + remaining;
    }

    private List<UUID> topPlayers() {
        int max = 0;
        for (UUID id : order) max = Math.max(max, wins.getOrDefault(id, 0));
        List<UUID> top = new ArrayList<>();
        if (max == 0) return top;
        for (UUID id : order) if (wins.getOrDefault(id, 0) == max) top.add(id);
        return top;
    }

    private String joinNames(List<UUID> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(i == ids.size() - 1 ? " & " : ", ");
            sb.append(names.getOrDefault(ids.get(i), "?"));
        }
        return sb.toString();
    }

    private Component standings() {
        List<UUID> sorted = new ArrayList<>(order);
        sorted.sort((a, b) -> wins.getOrDefault(b, 0) - wins.getOrDefault(a, 0));
        Component c = Component.text("Skor: ", NamedTextColor.GRAY);
        for (int i = 0; i < sorted.size(); i++) {
            UUID id = sorted.get(i);
            if (i > 0) c = c.append(Component.text("  |  ", NamedTextColor.DARK_GRAY));
            c = c.append(Component.text(names.getOrDefault(id, "?") + " ", NamedTextColor.WHITE))
                    .append(Component.text(String.valueOf(wins.getOrDefault(id, 0)), NamedTextColor.GOLD, TextDecoration.BOLD));
        }
        return c;
    }

    private void scheduleNext() {
        nextTask = Bukkit.getScheduler().runTaskLater(plugin, this::nextRound, 110L);
    }

    private void nextRound() {
        if (state != State.ENDED) return;
        if (!bonus) {
            roundOrder.clear();
            roundOrder.addAll(order);
            if (!replay) roundNo++;   // ronde di-void (cara mati sama) = nomor ronde sama, diulang
        } else {
            roundOrder.removeIf(id -> !order.contains(id));
        }
        replay = false;
        if (!settings.testMode && roundOrder.size() < 2) {
            if (roundOrder.size() == 1) showChampion(roundOrder.get(0)); else showNone();
            return;
        }
        if (!startRound()) {
            msg(Component.text("Gagal bikin world.", NamedTextColor.RED));
            showNone();
        }
    }

    private void showRound(String label, UUID winner, String note) {
        Title.Times times = Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3500), Duration.ofMillis(500));
        if (winner != null) {
            msg(Component.text(label + ": ", NamedTextColor.GOLD)
                    .append(Component.text(names.get(winner), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        } else {
            msg(Component.text(label + ": gak ada pemenang", NamedTextColor.GRAY));
        }
        msg(standings());
        if (note != null) msg(Component.text(note, NamedTextColor.AQUA));
        Component head = winner != null ? heads.get(winner) : null;
        for (Player p : participants()) {
            if (winner != null && head != null && plugin.packReady(p)) {
                p.showTitle(Title.title(WinScreen.title(Hud.BANNER_ROUND, List.of(head), plugin.headAdvance()),
                        WinScreen.subtitle(names.get(winner)), times));
            } else {
                p.showTitle(Title.title(Component.text(label, NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text(winner != null ? names.get(winner) : "Gak ada pemenang", NamedTextColor.YELLOW), times));
            }
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        if (winner != null) celebrate(Bukkit.getPlayer(winner));
    }

    private void showChampion(UUID winner) {
        msg(Component.text("Pemenang: ", NamedTextColor.GOLD)
                .append(Component.text(names.get(winner), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        if (settings.rounds > 1 || bonus) msg(standings());
        Title.Times times = Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(6), Duration.ofMillis(800));
        Component head = heads.get(winner);
        for (Player p : participants()) {
            if (head != null && plugin.packReady(p)) {
                p.showTitle(Title.title(WinScreen.title(head, plugin.headAdvance()),
                        WinScreen.subtitle(names.get(winner)), times));
            } else {
                p.showTitle(Title.title(Component.text("MENANG!", NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text(names.get(winner), NamedTextColor.YELLOW), times));
            }
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        celebrate(Bukkit.getPlayer(winner));
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, 160L);
    }

    private void showDraw(List<UUID> top) {
        int w = wins.getOrDefault(top.get(0), 0);
        msg(Component.text("SERI! ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text(joinNames(top) + " (" + w + " win)", NamedTextColor.WHITE)));
        msg(standings());
        List<Component> hs = new ArrayList<>();
        List<String> nm = new ArrayList<>();
        for (UUID id : top) {
            Component h = heads.get(id);
            if (h != null && hs.size() < WinScreen.MAX_HEADS) hs.add(h);
            nm.add(names.getOrDefault(id, "?"));
        }
        Title.Times times = Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(6), Duration.ofMillis(800));
        for (Player p : participants()) {
            if (!hs.isEmpty() && plugin.packReady(p)) {
                p.showTitle(Title.title(WinScreen.title(Hud.BANNER_DRAW, hs, plugin.headAdvance()),
                        WinScreen.subtitleNames(nm), times));
            } else {
                p.showTitle(Title.title(Component.text("SERI", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                        Component.text(joinNames(top), NamedTextColor.WHITE), times));
            }
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
        }
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, 160L);
    }

    private void showNone() {
        Title.Times times = Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(6), Duration.ofMillis(800));
        for (Player p : participants()) {
            p.showTitle(Title.title(Component.text("GAME SELESAI", NamedTextColor.GRAY, TextDecoration.BOLD),
                    Component.text("Gak ada pemenang", NamedTextColor.DARK_GRAY), times));
        }
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, 160L);
    }

    private void celebrate(Player w) {
        if (w == null || !w.isOnline() || world == null) return;
        for (int i = 0; i < 3; i++) {
            final int n = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!w.isOnline() || world == null || !w.getWorld().equals(world)) return;
                Location l = w.getLocation().add((n - 1) * 2.0, 3.0, 0);
                Firework fw = world.spawn(l, Firework.class);
                FireworkMeta m = fw.getFireworkMeta();
                m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                        .withColor(Color.YELLOW, Color.ORANGE).withFade(Color.WHITE).trail(true).build());
                fw.setFireworkMeta(m);
                fw.detonate();
            }, 4L + n * 8L);
        }
    }

    private void cleanup() {
        hideHud();
        for (UUID id : new ArrayList<>(order)) {
            Player p = Bukkit.getPlayer(id);
            PlayerSnapshot s = snaps.remove(id);
            if (p != null && s != null) s.restore(p);
        }
        manager.unregister(this);
        Bukkit.getScheduler().runTaskLater(plugin, () -> deleteWorld(), 20L);
    }

    @Override
    public void forceShutdown() {
        state = State.ENDED;
        hideHud();
        cancelTasks();
        if (endTask != null) endTask.cancel();
        if (nextTask != null) nextTask.cancel();
        for (UUID id : new ArrayList<>(order)) {
            Player p = Bukkit.getPlayer(id);
            PlayerSnapshot s = snaps.remove(id);
            if (p != null && s != null) s.restore(p);
        }
        deleteWorld();
    }

    private void cancelTasks() {
        if (countdownTask != null) countdownTask.cancel();
        if (tickTask != null) tickTask.cancel();
        if (hudTask != null) hudTask.cancel();
    }

    private void deleteWorld() {
        if (world == null) return;
        World w = world;
        world = null;
        deleteWorld(w);
    }

    private void deleteWorld(World w) {
        if (w == null) return;
        Location fallback = Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player p : new ArrayList<>(w.getPlayers())) p.teleport(fallback);
        java.io.File folder = w.getWorldFolder();
        if (Bukkit.unloadWorld(w, false)) {
            GameManager.deleteRecursive(folder);
        } else {
            plugin.getLogger().warning("Gagal unload world " + folder.getName());
        }
    }

    @Override
    public void onDamage(Player victim, EntityDamageEvent e) {
        UUID id = victim.getUniqueId();
        if (state != State.RUNNING || !alive.contains(id)) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent be) {
            Player a = attackerOf(be.getDamager());
            if (a != null && !a.equals(victim)) {
                e.setCancelled(true);
                return;
            }
        }
        if (e.getFinalDamage() >= victim.getHealth()) {
            e.setCancelled(true);
            handleDeath(victim, e.getCause());
        }
    }

    // ===== Aturan: gak boleh mati dengan cara jebakan yang sama dua kali =====

    private void markSwap(UUID id, Location dest) {
        swappedAt.put(id, System.currentTimeMillis());
        swapSpot.put(id, dest.clone());
    }

    private int trapWindowSec() {
        int cfg = plugin.getConfig().getInt("deathswap.trap-window-sec", DEFAULT_TRAP_WINDOW_SEC);
        // jendela gak boleh lebih dari setengah interval TP, biar kematian sendiri di spot sendiri gak ikut kehitung
        return Math.max(3, Math.min(cfg, settings.swapIntervalSec / 2));
    }

    private double trapRadius() {
        return Math.max(5.0, plugin.getConfig().getDouble("deathswap.trap-radius", DEFAULT_TRAP_RADIUS));
    }

    /**
     * Mati "kena jebakan lawan" = mati dalam {@link #trapWindowSec()} detik setelah TP DAN masih dalam
     * {@link #trapRadius()} block (horizontal) dari titik tujuan TP. Selain itu dianggap kesalahan sendiri
     * (nyebur lava sendiri, jatuh pas bangun jebakan, /kill, dll) dan lawan langsung menang.
     */
    private boolean isTrapDeath(Player p) {
        UUID id = p.getUniqueId();
        Long t = swappedAt.get(id);
        Location spot = swapSpot.get(id);
        if (t == null || spot == null) return false;
        if (System.currentTimeMillis() - t > trapWindowSec() * 1000L) return false;
        Location l = p.getLocation();
        if (l.getWorld() == null || !l.getWorld().equals(spot.getWorld())) return false;
        double dx = l.getX() - spot.getX();
        double dz = l.getZ() - spot.getZ();
        double r = trapRadius();
        return dx * dx + dz * dz <= r * r;
    }

    /** Kelompokkan DamageCause jadi "cara mati". null = bukan cara yang dicek (bunuh diri / command / custom). */
    private static String causeGroup(EntityDamageEvent.DamageCause c) {
        if (c == null) return null;
        return switch (c.name()) {
            case "KILL", "SUICIDE", "CUSTOM", "WORLD_BORDER" -> null;
            case "LAVA", "FIRE", "FIRE_TICK", "HOT_FLOOR", "CAMPFIRE", "MELTING" -> "BURN";
            case "FALL", "FLY_INTO_WALL" -> "FALL";
            case "DROWNING" -> "DROWN";
            case "SUFFOCATION", "CRAMMING" -> "SUFFOCATE";
            case "BLOCK_EXPLOSION", "ENTITY_EXPLOSION" -> "EXPLOSION";
            case "ENTITY_ATTACK", "ENTITY_SWEEP_ATTACK", "PROJECTILE", "THORNS", "SONIC_BOOM", "DRAGON_BREATH" -> "MOB";
            case "POISON", "MAGIC", "WITHER" -> "EFFECT";
            default -> c.name();   // VOID, CONTACT, FALLING_BLOCK, FREEZE, STARVATION, LIGHTNING, DRYOUT, ...
        };
    }

    private static String groupLabel(String g) {
        return switch (g) {
            case "BURN" -> "lava/api";
            case "FALL" -> "fall damage";
            case "VOID" -> "void";
            case "DROWN" -> "tenggelam";
            case "SUFFOCATE" -> "sesak napas";
            case "EXPLOSION" -> "ledakan";
            case "MOB" -> "serangan mob";
            case "EFFECT" -> "racun/efek";
            case "CONTACT" -> "kaktus/duri";
            case "FALLING_BLOCK" -> "benda jatuh";
            case "FREEZE" -> "beku";
            case "STARVATION" -> "kelaparan";
            case "LIGHTNING" -> "petir";
            default -> g.toLowerCase().replace('_', ' ');
        };
    }

    /** Titik masuk semua kematian pemain: tentuin jebakan / sendiri, cek cara mati yang sama, lalu eliminasi atau void ronde. */
    private void handleDeath(Player p, EntityDamageEvent.DamageCause cause) {
        UUID id = p.getUniqueId();
        if (state != State.RUNNING || !alive.contains(id)) return;
        if (!settings.uniqueDeath) {   // aturan dimatiin dari /panel: mati ya mati
            eliminate(p, causeText(cause));
            return;
        }
        String group = causeGroup(cause);
        boolean trap = group != null && isTrapDeath(p);
        if (trap) {
            Set<String> seen = trapDeaths.get(id);
            if (seen != null && seen.contains(group)) {
                voidRound(p, group);
                return;
            }
            roundTrapCause.put(id, group);
        }
        eliminate(p, causeText(cause) + (trap ? " (jebakan)" : " (sendiri)"));
    }

    private void commitTrapCauses() {
        for (Map.Entry<UUID, String> en : roundTrapCause.entrySet()) {
            trapDeaths.computeIfAbsent(en.getKey(), k -> new HashSet<>()).add(en.getValue());
        }
        roundTrapCause.clear();
    }

    /** Pemain mati kena jebakan dengan cara yang sama kayak ronde sebelumnya: seri, gak ada poin, ronde yang sama diulang. */
    private void voidRound(Player victim, String group) {
        if (state != State.RUNNING) return;
        state = State.ENDED;
        cancelTasks();
        hideHud();
        for (Player q : participants()) q.sendActionBar(Component.empty());
        roundTrapCause.clear();   // ronde batal, cara mati di ronde ini gak dicatat
        replay = true;

        UUID vid = victim.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {   // tahan korban selama jeda (damage udah dicancel pas state ENDED)
            if (!victim.isOnline() || victim.isDead()) return;
            victim.setHealth(20.0);
            victim.setFireTicks(0);
            victim.setFallDistance(0f);
            victim.setGameMode(GameMode.SPECTATOR);
        });

        String label = groupLabel(group);
        String doneLabel = bonus ? "BONUS MATCH"
                : "RONDE " + roundNo + (settings.rounds > 1 ? "/" + settings.rounds : "");
        msg(Component.text("SERI! ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text(names.getOrDefault(vid, "?"), NamedTextColor.WHITE))
                .append(Component.text(" mati kena " + label + " lagi, cara yang sama kayak ronde sebelumnya. "
                        + doneLabel + " diulang, gak ada poin.", NamedTextColor.GRAY)));
        msg(standings());

        Title.Times times = Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3500), Duration.ofMillis(500));
        Component head = heads.get(vid);
        for (Player p : participants()) {
            if (head != null && plugin.packReady(p)) {
                p.showTitle(Title.title(WinScreen.title(Hud.BANNER_DRAW, List.of(head), plugin.headAdvance()),
                        Component.text("Cara mati sama, ronde diulang", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD), times));
            } else {
                p.showTitle(Title.title(Component.text("SERI", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                        Component.text("Cara mati sama, ronde diulang", NamedTextColor.GRAY), times));
            }
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
        }
        scheduleNext();
    }

    private String causeText(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case VOID -> "jatuh ke void";
            case FALL -> "fall damage";
            case LAVA -> "lava";
            default -> "mati";
        };
    }

    @Override
    public void onMove(Player p, PlayerMoveEvent e) {
        if (state != State.COUNTDOWN) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
            e.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                    to.getYaw(), to.getPitch()));
        }
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent e) {
        if (state != State.RUNNING) e.setCancelled(true);
    }

    @Override
    public void onBlockBreak(BlockBreakEvent e) {
        if (state != State.RUNNING) e.setCancelled(true);
    }

    @Override
    public void onDeath(Player p, PlayerDeathEvent e) {
        e.deathMessage(null);
        deathLocs.put(p.getUniqueId(), p.getLocation());
        EntityDamageEvent last = p.getLastDamageCause();
        handleDeath(p, last != null ? last.getCause() : null);
    }

    @Override
    public void onRespawn(Player p, PlayerRespawnEvent e) {
        Location back = deathLocs.getOrDefault(p.getUniqueId(), spectatorLoc);
        if (back == null) return;
        e.setRespawnLocation(back);
        if (state == State.ENDED) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            p.getInventory().clear();
            p.setGameMode(GameMode.SPECTATOR);
        });
    }

    @Override
    public void onQuit(Player p) {
        UUID id = p.getUniqueId();
        boolean wasAlive = alive.contains(id);
        if (state == State.RUNNING && wasAlive) {
            alive.remove(id);
            msg(Component.text(p.getName() + " keluar", NamedTextColor.GRAY));
        } else {
            alive.remove(id);
        }
        PlayerSnapshot s = snaps.remove(id);
        if (s != null) s.restore(p);
        if (bossBar != null) p.hideBossBar(bossBar);
        order.remove(id);
        manager.forget(id);
        updateHud();
        checkWin();
    }

    @Override
    public void forfeit(Player p) {
        if (state == State.RUNNING && alive.contains(p.getUniqueId())) {
            eliminate(p, "nyerah");
        }
    }

    private Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    private List<Player> participants() {
        List<Player> list = new ArrayList<>();
        for (UUID id : order) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) list.add(p);
        }
        return list;
    }

    private void msg(Component c) {
        Component full = Component.text("[MINIGAMES] ", NamedTextColor.GOLD).append(c);
        for (Player p : participants()) p.sendMessage(full);
    }
}
