package id.zetminigames.battleroyale;

import id.zetminigames.ZetMinigames;
import id.zetminigames.core.GameManager;
import id.zetminigames.core.GameSettings;
import id.zetminigames.core.Match;
import id.zetminigames.core.PlayerSnapshot;
import id.zetminigames.hud.CustomTitle;
import id.zetminigames.hud.Hud;
import id.zetminigames.hud.WinScreen;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class BattleRoyaleGame implements Match, Listener {
    private enum State { LOADING, READY, COUNTDOWN, RUNNING, ENDED }

    private static final int COUNTDOWN_SEC = 5;
    private static final int SLOW_FALL_TICKS = 20 * 600;
    private static final int CAGE_HEIGHT = 5;
    private static final int FORCED_STAGES = 2;
    private static final int CHEST_CELL = 14;
    private static final int CHEST_JITTER = 2;
    private static final long NOON = 6000L;

    private final ZetMinigames plugin;
    private final GameManager manager;
    private final GameSettings settings;
    private final boolean testMode;
    private final List<UUID> order = new ArrayList<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Component> heads = new HashMap<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Set<UUID> falling = new HashSet<>();
    private final Map<UUID, PlayerSnapshot> snaps = new HashMap<>();
    private final Map<UUID, Location> deathLocs = new HashMap<>();
    private final List<BukkitTask> temp = new ArrayList<>();
    private final Set<Long> processedChunks = new HashSet<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private final Set<Long> placed = new HashSet<>();

    private World world;
    private Location skyCenter;
    private State state = State.LOADING;
    private BukkitTask tickTask;
    private BukkitTask endTask;
    private long matchStart;
    private long releaseAt;
    private int baseStages;
    private int pvpDelay;
    private boolean pvpOn;
    private int nextShrink;
    private int[] shrinkAt = new int[0];
    private int[] shrinkLen = new int[0];
    private int[] sizes = new int[0];
    private int startCount;
    private int totalSec;

    public BattleRoyaleGame(ZetMinigames plugin, GameManager manager, List<Player> players, GameSettings settings) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
        this.testMode = settings.testMode;
        for (Player p : players) {
            order.add(p.getUniqueId());
            names.put(p.getUniqueId(), p.getName());
        }
    }

    @Override
    public List<UUID> playerIds() { return order; }

    @Override
    public boolean isTestMode() { return testMode; }

    @Override
    public int rounds() { return 1; }

    /** Mode tes: tombol "Selesai (tes)" di /panel. Pemain yang klik jadi pemenang, game langsung kelar. */
    @Override
    public void testFinish(Player p) {
        if (!testMode) return;
        if (state == State.LOADING) {
            p.sendMessage(Component.text("Arena masih disiapin, tunggu bentar.", NamedTextColor.RED));
            return;
        }
        if (state == State.ENDED) return;
        finish(p.getUniqueId());
    }

    @Override
    public void testDraw(Player p) {}

    /** Battle Royale gak punya konsep seri, jadi tombol "Seri (tes)" gak ditampilin. */
    @Override
    public boolean hasTestDraw() { return false; }

    private int skyY() {
        return cfgInt("sky.y", 300);
    }

    private int skyRadius() {
        return Math.max(4, cfgInt("sky.radius", 24));
    }

    private int cfgInt(String key, int def) {
        return plugin.getConfig().getInt("battleroyale." + key, def);
    }

    private double cfgDouble(String key, double def) {
        return plugin.getConfig().getDouble("battleroyale." + key, def);
    }

    @Override
    public boolean begin() {
        for (Player p : participants()) heads.put(p.getUniqueId(), Hud.head(p));
        Bukkit.getPluginManager().registerEvents(this, plugin);
        String worldName = "pof_br_" + Long.toString(System.currentTimeMillis(), 36);
        World created = new WorldCreator(worldName).createWorld();
        if (created == null) {
            HandlerList.unregisterAll(this);
            return false;
        }
        world = created;
        int cx = cfgInt("center.x", 0);
        int cz = cfgInt("center.z", 0);
        world.setDifficulty(Difficulty.NORMAL);
        world.setSpawnLocation(cx, skyY(), cz);
        WorldBorder wb = world.getWorldBorder();
        wb.setCenter(cx + 0.5, cz + 0.5);
        wb.setSize(cfgDouble("zone.start-size", 800.0));
        wb.setDamageBuffer(0.0);
        wb.setDamageAmount(cfgDouble("zone.damage", 1.0));
        wb.setWarningDistance(12);
        world.setTime(NOON);
        temp.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (world != null && state != State.ENDED) world.setTime(NOON);
        }, 20L, 20L));

        msg(Component.text("Menyiapkan arena...", NamedTextColor.YELLOW));
        loadCenter(cx, cz);
        return true;
    }

    private void loadCenter(int cx, int cz) {
        int r = skyRadius();
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (int x = (cx - r) >> 4; x <= (cx + r) >> 4; x++) {
            for (int z = (cz - r) >> 4; z <= (cz + r) >> 4; z++) {
                loads.add(world.getChunkAtAsync(x, z, true));
            }
        }
        CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((v, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (state == State.ENDED || world == null) return;
                    if (error != null) {
                        fail("Gagal bikin arena.");
                        return;
                    }
                    for (Chunk loaded : world.getLoadedChunks()) placeChests(loaded);
                    sky(true);
                    enterArena();
                }));
    }

    private void sky(boolean build) {
        int r = skyRadius();
        int y = skyY() - 1;
        int cx = cfgInt("center.x", 0);
        int cz = cfgInt("center.z", 0);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.5) continue;
                boolean rim = d > r - 1.5;
                Material floor = !build ? Material.AIR
                        : rim ? Material.POLISHED_BLACKSTONE
                        : Math.abs(d - r / 2.0) < 0.6 ? Material.GOLD_BLOCK
                        : Material.SMOOTH_QUARTZ;
                world.getBlockAt(cx + dx, y, cz + dz).setType(floor, false);
                Material cage = build ? Material.GLASS : Material.AIR;
                if (rim) {
                    for (int h = 1; h <= CAGE_HEIGHT; h++) {
                        world.getBlockAt(cx + dx, y + h, cz + dz).setType(cage, false);
                    }
                }
                world.getBlockAt(cx + dx, y + CAGE_HEIGHT + 1, cz + dz).setType(cage, false);
            }
        }
    }

    private void fail(String reason) {
        if (state == State.ENDED) return;
        state = State.ENDED;
        cancelTasks();
        msg(Component.text(reason, NamedTextColor.RED));
        for (UUID id : new ArrayList<>(order)) {
            Player p = Bukkit.getPlayer(id);
            PlayerSnapshot s = snaps.remove(id);
            if (p != null && s != null) s.restore(p);
        }
        deleteWorld();
        manager.unregister(this);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (world == null || !e.getWorld().equals(world)) return;
        Chunk chunk = e.getChunk();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (state != State.ENDED && world != null && chunk.isLoaded()) placeChests(chunk);
        });
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        if (world != null && e.getLocation().getWorld().equals(world)) e.setCancelled(true);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (order.contains(e.getPlayer().getUniqueId())) e.message(null);
    }

    private void placeChests(Chunk chunk) {
        if (!processedChunks.add(chunk.getChunkKey())) return;
        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;
        int centerX = cfgInt("center.x", 0);
        int centerZ = cfgInt("center.z", 0);
        double half = cfgDouble("zone.start-size", 800.0) / 2.0;
        int skyR = skyRadius() + 1;
        for (int i = Math.floorDiv(minX - 10, CHEST_CELL); i <= Math.floorDiv(minX + 20, CHEST_CELL); i++) {
            for (int j = Math.floorDiv(minZ - 10, CHEST_CELL); j <= Math.floorDiv(minZ + 20, CHEST_CELL); j++) {
                Random rng = new Random(world.getSeed() ^ (i * 73856093L) ^ (j * 19349663L));
                int x = i * CHEST_CELL + CHEST_CELL / 2 + rng.nextInt(CHEST_JITTER * 2 + 1) - CHEST_JITTER;
                int z = j * CHEST_CELL + CHEST_CELL / 2 + rng.nextInt(CHEST_JITTER * 2 + 1) - CHEST_JITTER;
                if (x < minX || x > minX + 15 || z < minZ || z > minZ + 15) continue;
                if (Math.abs(x - centerX) > half || Math.abs(z - centerZ) > half) continue;
                int dx = x - centerX;
                int dz = z - centerZ;
                if (dx * dx + dz * dz <= skyR * skyR) continue;
                int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (world.getBlockAt(x, y, z).isLiquid()) continue;
                Block spot = world.getBlockAt(x, y + 1, z);
                if (!spot.isEmpty() && !Tag.LEAVES.isTagged(spot.getType())) continue;
                spot.setType(Material.CHEST, false);
                if (spot.getState() instanceof Chest chest) BrLoot.fill(chest.getBlockInventory());
            }
        }
    }

    private void enterArena() {
        if (state == State.ENDED) return;
        double sx = cfgInt("center.x", 0) + 0.5;
        double sy = skyY();
        double sz = cfgInt("center.z", 0) + 0.5;
        skyCenter = new Location(world, sx, sy, sz);
        List<Location> spots = skySpots(sx, sy, sz);
        state = State.READY;
        int i = 0;
        for (Player p : participants()) {
            UUID id = p.getUniqueId();
            snaps.computeIfAbsent(id, k -> new PlayerSnapshot(p));
            prepare(p);
            alive.add(id);
            p.teleport(spots.get(i % spots.size()));
            i++;
        }
        startCount = alive.size();
        if (!testMode && alive.size() <= 1) {
            checkWin();
            return;
        }
        readyPhase();
    }

    private void readyPhase() {
        int sec = cfgInt("ready-seconds", 30);
        msg(Component.text("Kalian punya " + sec + " detik di platform buat siap-siap.", NamedTextColor.YELLOW));
        BukkitTask[] self = new BukkitTask[1];
        int[] left = {sec};
        final int total = sec;
        self[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.READY) {
                self[0].cancel();
                return;
            }
            if (left[0] <= 0) {
                self[0].cancel();
                startCountdown();
                return;
            }
            for (Player p : participants()) readyHud(p, left[0], total);
            left[0]--;
        }, 0L, 20L);
        temp.add(self[0]);
    }

    private int pvpDelaySec() {
        return cfgInt("pvp-delay-seconds", 30);
    }

    private void readyHud(Player p, int left, int total) {
        String timer = clock(left);
        if (!plugin.packReady(p)) {
            p.sendActionBar(Component.text("BERSIAP  ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .append(Component.text("Countdown mulai dalam " + timer, NamedTextColor.WHITE)));
            return;
        }
        UUID id = p.getUniqueId();
        BossBar bar = bars.get(id);
        if (bar == null) {
            bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            bars.put(id, bar);
            p.showBossBar(bar);
        }
        bar.name(Hud.buildBr(new Hud.BrData("BERSIAP", 0xFFD23C, timer, left / (double) Math.max(1, total),
                "PVP " + Hud.fmtTime(pvpDelaySec()), 0xFFAA55, alive.size(), Math.max(startCount, alive.size()),
                0, "DI PLATFORM", 0x55FF55, 0, testMode)));
    }

    private void startCountdown() {
        if (state != State.READY) return;
        state = State.COUNTDOWN;
        matchStart = System.currentTimeMillis();
        pvpDelay = pvpDelaySec();
        buildSchedule();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 10L);
        countdown(COUNTDOWN_SEC, this::release);
    }

    private static String clock(int sec) {
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    private List<Location> skySpots(double sx, double sy, double sz) {
        int r = skyRadius() - 2;
        List<Location> spots = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                if (dx * dx + dz * dz > r * r) continue;
                spots.add(new Location(world, sx + dx, sy, sz + dz, random.nextFloat() * 360f - 180f, 0f));
            }
        }
        if (spots.isEmpty()) spots.add(new Location(world, sx, sy, sz));
        Collections.shuffle(spots);
        return spots;
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

    private void countdown(int from, Runnable done) {
        BukkitTask[] self = new BukkitTask[1];
        int[] c = {from};
        self[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state == State.ENDED) {
                self[0].cancel();
                return;
            }
            if (c[0] > 0) {
                int n = c[0];
                for (Player p : participants()) {
                    CustomTitle.show(plugin, p, CustomTitle.count(n),
                            Component.text(String.valueOf(n), NamedTextColor.YELLOW, TextDecoration.BOLD),
                            Component.text("Siap-siap!", NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100)));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                }
                c[0]--;
            } else {
                self[0].cancel();
                done.run();
            }
        }, 10L, 20L);
        temp.add(self[0]);
    }

    private void release() {
        if (state != State.COUNTDOWN) return;
        state = State.RUNNING;
        releaseAt = System.currentTimeMillis();
        sky(false);
        pvpOn = pvpDelay <= 0;
        if (!pvpOn) msg(Component.text("PVP aktif dalam " + pvpDelay + " detik", NamedTextColor.YELLOW));
        for (Player p : participants()) {
            UUID id = p.getUniqueId();
            if (!alive.contains(id)) continue;
            falling.add(id);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false));
            CustomTitle.show(plugin, p, CustomTitle.banner(CustomTitle.BANNER_GO),
                    Component.text("MULAI!", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("Turun dan cari loot", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300)));
            p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }

    private void buildSchedule() {
        int n = Math.max(1, cfgInt("zone.stages", 6));
        double startSize = cfgDouble("zone.start-size", 800.0);
        double finalSize = cfgDouble("zone.final-size", 20.0);
        int total = cfgInt("zone.total-seconds", 1200);
        total = Math.max(n * 2, total);
        totalSec = total;
        double minWait = cfgInt("zone.min-wait-seconds", 45);
        double minShrink = cfgInt("zone.min-shrink-seconds", 30);

        double scale = Math.min(1.0, total / (n * (minWait + minShrink)));
        minWait *= scale;
        minShrink *= scale;
        double spare = total - n * (minWait + minShrink);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double[] weights = new double[n * 2];
        double sum = 0.0;
        for (int k = 0; k < weights.length; k++) {
            weights[k] = 0.15 - Math.log(1.0 - random.nextDouble());
            sum += weights[k];
        }

        baseStages = n;
        shrinkAt = new int[n + FORCED_STAGES];
        shrinkLen = new int[n + FORCED_STAGES];
        sizes = new int[n + FORCED_STAGES];
        double t = 0.0;
        for (int i = 0; i < n; i++) {
            double wait = minWait + spare * weights[i * 2] / sum;
            double shrink = minShrink + spare * weights[i * 2 + 1] / sum;
            t += wait;
            shrinkAt[i] = (int) Math.round(t);
            shrinkLen[i] = Math.max(1, (int) Math.round(shrink));
            t += shrink;
            double size = startSize * Math.pow(finalSize / startSize, (i + 1) / (double) n);
            sizes[i] = (int) Math.max(finalSize, Math.round(size));
        }
        sizes[n - 1] = (int) finalSize;
        shrinkLen[n - 1] = Math.max(1, total - shrinkAt[n - 1]);

        int gap = cfgInt("zone.force-gap-seconds", 300);
        gap = Math.max(2, gap);
        int forceLen = Math.max(1, Math.min(cfgInt("zone.force-shrink-seconds", 60), gap - 1));
        int[] forceSizes = {cfgInt("zone.force-size-1", 10), cfgInt("zone.force-size-2", 1)};
        for (int k = 0; k < FORCED_STAGES; k++) {
            int end = total + gap * (k + 1);
            shrinkAt[n + k] = end - forceLen;
            shrinkLen[n + k] = forceLen;
            sizes[n + k] = Math.max(1, forceSizes[k]);
        }
        nextShrink = 0;
    }

    // ===================== HUD / ZONA =====================

    private static final double WALL_RANGE = 10.0;

    private record Phase(String label, int color, int remaining, double frac, boolean shrinking, boolean last) {}

    /** Fase zona di detik ke-sec: lagi nunggu, lagi mengecil, atau udah final. */
    private Phase phaseAt(int sec) {
        int n = shrinkAt.length;
        for (int i = 0; i < n; i++) {
            int prevEnd = i == 0 ? 0 : shrinkAt[i - 1] + shrinkLen[i - 1];
            if (sec < shrinkAt[i]) {
                int span = Math.max(1, shrinkAt[i] - prevEnd);
                int left = shrinkAt[i] - sec;
                String label = i < baseStages ? "ZONA " + (i + 1) + "/" + baseStages
                        : "ZONA " + sizes[i - 1] + "x" + sizes[i - 1];
                return new Phase(label, 0xFFD23C, left, left / (double) span, false, false);
            }
            int endAt = shrinkAt[i] + shrinkLen[i];
            if (sec < endAt) {
                int left = endAt - sec;
                return new Phase("MENGECIL", 0xFF5555, left, left / (double) Math.max(1, shrinkLen[i]), true, false);
            }
        }
        return new Phase("ZONA FINAL", 0xFF5555, 0, 0.0, false, true);
    }

    private void tick() {
        if ((state != State.RUNNING && state != State.COUNTDOWN) || world == null) return;
        long now = System.currentTimeMillis();
        int sec = (int) ((now - matchStart) / 1000L);
        WorldBorder wb = world.getWorldBorder();
        int pvpLeft = pvpDelay;

        if (state == State.RUNNING) {
            pvpLeft = pvpOn ? 0 : Math.max(0, (int) Math.ceil((releaseAt + pvpDelay * 1000L - now) / 1000.0));

            // zona mulai mengecil
            while (nextShrink < shrinkAt.length && sec >= shrinkAt[nextShrink]) {
                int i = nextShrink++;
                long from = Math.round(wb.getSize());
                wb.setSize(sizes[i], shrinkLen[i]);
                Component big = Component.text("ZONA MENGECIL", NamedTextColor.RED, TextDecoration.BOLD);
                Component small = Component.text(from + "x" + from + "  >  " + sizes[i] + "x" + sizes[i]
                        + "  (" + Hud.fmtTime(shrinkLen[i]) + ")", NamedTextColor.YELLOW);
                Title.Times times = Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2200), Duration.ofMillis(400));
                for (Player p : participants()) {
                    p.showTitle(Title.title(big, small, times));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1f);
                }
                msg(Component.text("Zona mengecil jadi " + sizes[i] + "x" + sizes[i], NamedTextColor.RED, TextDecoration.BOLD));
            }

            // peringatan sebelum zona mengecil (30s, 10s, lalu hitung mundur 5..1)
            if (nextShrink < shrinkAt.length) {
                int i = nextShrink;
                int left = shrinkAt[i] - sec;
                if ((left == 30 || left == 10) && warned.add(i + ":" + left)) {
                    msg(Component.text("Zona mengecil jadi " + sizes[i] + "x" + sizes[i] + " dalam " + left + " detik",
                            NamedTextColor.YELLOW));
                    for (Player p : participants()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
                } else if (left >= 1 && left <= 5 && warned.add(i + ":" + left)) {
                    for (Player p : participants()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
                }
            }

            if (!pvpOn && pvpLeft <= 0) {
                pvpOn = true;
                msg(Component.text("PVP aktif!", NamedTextColor.RED, TextDecoration.BOLD));
                for (Player p : participants()) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1f);
                }
            } else if (!pvpOn) {
                if (pvpLeft == 10 && warned.add("pvp:10")) {
                    msg(Component.text("PVP aktif dalam 10 detik", NamedTextColor.YELLOW));
                } else if (pvpLeft >= 1 && pvpLeft <= 5 && warned.add("pvp:" + pvpLeft)) {
                    for (Player p : participants()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
                }
            }
        }

        Phase ph = phaseAt(sec);
        for (Player p : participants()) {
            if (!p.getWorld().equals(world)) continue;
            updateHud(p, sec, pvpLeft, ph, wb);
            if (state == State.RUNNING) drawWall(p, wb);
        }
    }

    /** HUD custom (bossbar + resource pack) kalau pack udah ke-load, kalau belum pakai actionbar biasa. */
    private void updateHud(Player p, int sec, int pvpLeft, Phase ph, WorldBorder wb) {
        UUID id = p.getUniqueId();
        Location loc = p.getLocation();
        Location c = wb.getCenter();
        double half = wb.getSize() / 2.0;
        double dx = c.getX() - loc.getX();
        double dz = c.getZ() - loc.getZ();
        double edge = half - Math.max(Math.abs(dx), Math.abs(dz));   // plus = di dalam zona, minus = di luar

        // arah ke tengah zona relatif ke arah pandang pemain (0 = depan, searah jarum jam)
        double toCenter = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = ((toCenter - loc.getYaw()) % 360.0 + 540.0) % 360.0 - 180.0;
        int arrow = Math.floorMod((int) Math.round(rel / 45.0), 8);

        boolean outside = edge < 0;
        int distColor = outside ? 0xFF5555 : edge < 15 ? 0xFFAA00 : 0x55FF55;
        String distText = outside ? "LUAR ZONA " + (int) Math.ceil(-edge) + "m" : "TEPI ZONA " + (int) edge + "m";
        String pvpText = pvpOn ? "PVP AKTIF" : "PVP " + Hud.fmtTime(pvpLeft);
        int pvpColor = pvpOn ? 0xFF5555 : 0xFFAA55;
        String timer = clock(sec);
        String phaseText = ph.last() ? ph.label() : ph.label() + " " + Hud.fmtTime(ph.remaining());
        int killCount = kills.getOrDefault(id, 0);

        if (plugin.packReady(p)) {
            BossBar bar = bars.get(id);
            if (bar == null) {
                bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                bars.put(id, bar);
                p.showBossBar(bar);
            }
            bar.name(Hud.buildBr(new Hud.BrData(phaseText, ph.color(), timer, ph.frac(), pvpText, pvpColor,
                    alive.size(), Math.max(startCount, alive.size()), arrow, distText, distColor, killCount, testMode)));
            return;
        }

        Component sep = Component.text("  |  ", NamedTextColor.DARK_GRAY);
        Component line = Component.empty();
        if (testMode) line = line.append(Component.text("[TES] ", NamedTextColor.GOLD, TextDecoration.BOLD));
        line = line.append(Component.text(timer, NamedTextColor.WHITE))
                .append(sep).append(Component.text(phaseText, TextColor.color(ph.color())))
                .append(sep).append(Component.text(pvpText, TextColor.color(pvpColor)))
                .append(sep).append(Component.text(Hud.ARROW_TEXT[arrow] + " " + distText, TextColor.color(distColor)))
                .append(sep).append(Component.text(alive.size() + " hidup", NamedTextColor.GREEN))
                .append(sep).append(Component.text(killCount + " kill", NamedTextColor.GOLD));
        p.sendActionBar(line);
    }

    /** Dinding zona dari partikel merah, cuma digambar di dekat pemain biar ringan. */
    private void drawWall(Player p, WorldBorder wb) {
        Location c = wb.getCenter();
        double half = wb.getSize() / 2.0;
        double minX = c.getX() - half;
        double maxX = c.getX() + half;
        double minZ = c.getZ() - half;
        double maxZ = c.getZ() + half;
        Location l = p.getLocation();
        double px = l.getX();
        double py = l.getY();
        double pz = l.getZ();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 70, 70), 1.6f);

        if (Math.abs(px - minX) < WALL_RANGE) wallAlongZ(p, minX, pz, py, minZ, maxZ, dust);
        if (Math.abs(px - maxX) < WALL_RANGE) wallAlongZ(p, maxX, pz, py, minZ, maxZ, dust);
        if (Math.abs(pz - minZ) < WALL_RANGE) wallAlongX(p, minZ, px, py, minX, maxX, dust);
        if (Math.abs(pz - maxZ) < WALL_RANGE) wallAlongX(p, maxZ, px, py, minX, maxX, dust);
    }

    private void wallAlongZ(Player p, double x, double pz, double py, double minZ, double maxZ, Particle.DustOptions dust) {
        for (double z = Math.max(pz - 8, minZ); z <= Math.min(pz + 8, maxZ); z += 2.0) {
            for (double y = py - 3; y <= py + 5; y += 2.0) {
                p.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust);
            }
        }
    }

    private void wallAlongX(Player p, double z, double px, double py, double minX, double maxX, Particle.DustOptions dust) {
        for (double x = Math.max(px - 8, minX); x <= Math.min(px + 8, maxX); x += 2.0) {
            for (double y = py - 3; y <= py + 5; y += 2.0) {
                p.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust);
            }
        }
    }

    private void hideHud() {
        for (UUID id : new ArrayList<>(order)) {
            Player p = Bukkit.getPlayer(id);
            BossBar b = bars.get(id);
            if (p != null && b != null) p.hideBossBar(b);
        }
        bars.clear();
    }

    private void eliminate(Player p, String cause, boolean dropItems) {
        UUID id = p.getUniqueId();
        if (state != State.RUNNING || !alive.remove(id)) return;
        falling.remove(id);
        msg(Component.text(names.get(id), NamedTextColor.RED)
                .append(Component.text(" " + cause, NamedTextColor.GRAY))
                .append(Component.text("  (" + alive.size() + " sisa)", NamedTextColor.DARK_GRAY)));
        if (dropItems) dropAll(p);
        p.removePotionEffect(PotionEffectType.SLOW_FALLING);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || p.isDead()) return;
            p.setHealth(20.0);
            p.setFireTicks(0);
            p.setFallDistance(0f);
            p.setGameMode(GameMode.SPECTATOR);
            if (world != null && p.getLocation().getY() < world.getMinHeight()) p.teleport(skyCenter);
            showEliminated(p);
        });
        checkWin();
    }

    private void showEliminated(Player p) {
        CustomTitle.show(plugin, p, CustomTitle.banner(CustomTitle.BANNER_ELIM),
                Component.text("TERELIMINASI", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Nonton aja", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(3000), Duration.ofMillis(500)));
    }

    private void dropAll(Player p) {
        if (world == null) return;
        Location at = p.getLocation();
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) world.dropItemNaturally(at, item);
        }
        p.getInventory().clear();
    }

    private void checkWin() {
        if (state == State.ENDED || state == State.LOADING) return;
        if (testMode) {
            // mode tes: sisa 1 pemain TIDAK menang. Game cuma kelar kalau semua mati/nyerah, atau klik "Selesai (tes)".
            if (alive.isEmpty()) finish(null);
            return;
        }
        if (alive.size() <= 1) {
            finish(alive.isEmpty() ? null : alive.iterator().next());
        }
    }

    private void finish(UUID winner) {
        if (state == State.ENDED) return;
        state = State.ENDED;
        cancelTasks();
        for (Player p : participants()) {
            p.sendActionBar(Component.empty());
            p.removePotionEffect(PotionEffectType.SLOW_FALLING);
        }
        Title.Times times = Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(6), Duration.ofMillis(800));
        if (winner == null) {
            for (Player p : participants()) {
                p.showTitle(Title.title(Component.text("GAME SELESAI", NamedTextColor.GRAY, TextDecoration.BOLD),
                        Component.text("Gak ada pemenang", NamedTextColor.DARK_GRAY), times));
            }
        } else {
            msg(Component.text("Pemenang: ", NamedTextColor.GOLD)
                    .append(Component.text(names.get(winner), NamedTextColor.YELLOW, TextDecoration.BOLD)));
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
        for (UUID id : new ArrayList<>(order)) {
            Player p = Bukkit.getPlayer(id);
            PlayerSnapshot s = snaps.remove(id);
            if (p != null) p.removePotionEffect(PotionEffectType.SLOW_FALLING);
            if (p != null && s != null) s.restore(p);
        }
        manager.unregister(this);
        Bukkit.getScheduler().runTaskLater(plugin, this::deleteWorld, 20L);
    }

    @Override
    public void forceShutdown() {
        state = State.ENDED;
        cancelTasks();
        if (endTask != null) endTask.cancel();
        for (UUID id : new ArrayList<>(order)) {
            Player p = Bukkit.getPlayer(id);
            PlayerSnapshot s = snaps.remove(id);
            if (p != null) p.removePotionEffect(PotionEffectType.SLOW_FALLING);
            if (p != null && s != null) s.restore(p);
        }
        deleteWorld();
    }

    private void cancelTasks() {
        hideHud();
        HandlerList.unregisterAll(this);
        if (tickTask != null) tickTask.cancel();
        for (BukkitTask t : new ArrayList<>(temp)) t.cancel();
        temp.clear();
    }

    private void deleteWorld() {
        if (world == null) return;
        World w = world;
        world = null;
        Location fallback = Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player p : new ArrayList<>(w.getPlayers())) p.teleport(fallback);
        File folder = w.getWorldFolder();
        if (Bukkit.unloadWorld(w, false)) {
            GameManager.deleteRecursive(folder);
        } else {
            plugin.getLogger().warning("Gagal unload world " + folder.getName());
        }
    }

    @Override
    public void onDamage(Player victim, EntityDamageEvent e) {
        if (state != State.RUNNING || !alive.contains(victim.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent be) {
            Player attacker = attackerOf(be.getDamager());
            if (attacker != null && !attacker.equals(victim)
                    && (!pvpOn || !alive.contains(attacker.getUniqueId()))) {
                e.setCancelled(true);
            }
        }
    }

    @Override
    public void onMove(Player p, PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null) return;
        UUID id = p.getUniqueId();
        if (state == State.COUNTDOWN && alive.contains(id)) {
            Location from = e.getFrom();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                e.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
            }
            return;
        }
        if (state == State.RUNNING && falling.contains(id) && to.getY() < skyCenter.getY() - 6) {
            Material below = to.clone().subtract(0, 0.2, 0).getBlock().getType();
            if (below.isSolid() || p.isInWater()) {
                falling.remove(id);
                p.removePotionEffect(PotionEffectType.SLOW_FALLING);
            }
        }
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent e) {
        if (state != State.RUNNING || !alive.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        placed.add(e.getBlockPlaced().getBlockKey());
    }

    @Override
    public void onBlockBreak(BlockBreakEvent e) {
        boolean own = state == State.RUNNING && alive.contains(e.getPlayer().getUniqueId())
                && placed.remove(e.getBlock().getBlockKey());
        if (!own) e.setCancelled(true);
    }

    @Override
    public void onDeath(Player p, PlayerDeathEvent e) {
        e.deathMessage(null);
        if (state == State.ENDED) {
            deathLocs.put(p.getUniqueId(), p.getLocation());
            e.setKeepInventory(true);
            e.getDrops().clear();
            e.setKeepLevel(true);
            e.setDroppedExp(0);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && p.isDead()) p.spigot().respawn();
            });
            return;
        }
        deathLocs.put(p.getUniqueId(), p.getLocation());
        Player killer = p.getKiller();
        if (killer != null && !killer.equals(p)) kills.merge(killer.getUniqueId(), 1, Integer::sum);
        eliminate(p, killer != null ? "dibunuh " + killer.getName() : "mati", false);
    }

    @Override
    public void onRespawn(Player p, PlayerRespawnEvent e) {
        Location back = deathLocs.get(p.getUniqueId());
        if (back == null || world == null || back.getY() < world.getMinHeight()) back = skyCenter;
        if (back == null) return;
        e.setRespawnLocation(back);
        if (state == State.ENDED) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            p.setGameMode(GameMode.SPECTATOR);
            showEliminated(p);
        });
    }

    @Override
    public void onQuit(Player p) {
        UUID id = p.getUniqueId();
        if (state == State.RUNNING && alive.contains(id)) {
            msg(Component.text(p.getName() + " keluar", NamedTextColor.GRAY));
            dropAll(p);
        }
        alive.remove(id);
        falling.remove(id);
        BossBar myBar = bars.remove(id);
        if (myBar != null) p.hideBossBar(myBar);
        p.removePotionEffect(PotionEffectType.SLOW_FALLING);
        PlayerSnapshot s = snaps.remove(id);
        if (s != null) s.restore(p);
        order.remove(id);
        manager.forget(id);
        checkWin();
    }

    @Override
    public void forfeit(Player p) {
        if (state == State.RUNNING && alive.contains(p.getUniqueId())) {
            eliminate(p, "nyerah", true);
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
