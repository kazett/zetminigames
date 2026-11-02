package id.zetminigames.pof;

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
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Satu match Pillar of Fortune (satu world void sendiri). */
public class PofGame implements Match {

    @Override
    public boolean allowDrop() {
        return true;
    }
    public enum State { COUNTDOWN, RUNNING, ENDED }

    private record Hit(UUID attacker, long at) {}

    // ===== Konstanta layout =====
    static final int FLOOR_Y = 64;
    /** Tinggi pilar dari lantai. */
    static final int PILLAR_H = 20;
    static final int TOP_Y = FLOOR_Y + PILLAR_H;
    /** Jarak minimal antar pilar yang bersebelahan. */
    static final double PILLAR_SPACING = 15.0;
    /** Border awal = pilar terluar + sekian block. */
    static final int BORDER_MARGIN = 10;
    /** Ukuran border akhir saat mengecil. */
    static final double FINAL_BORDER_SIZE = 6.0;
    static final int COUNTDOWN_SEC = 5;
    /** Waktu siap-siap (detik) sebelum countdown 5..1: pemain udah di pilar tapi belum bisa gerak (nunggu semua kelar load). */
    static final int READY_SEC = 10;
    /** Cadangan kalau world null: lava maks sekian block di atas puncak pilar. */
    static final int LAVA_ABOVE_TOP = 4;
    /** Batas jumlah block lava yang di-set per tick (biar gak lag pas lapisan baru dibikin). */
    private static final int LAVA_BLOCKS_PER_TICK = 1500;

    private final ZetMinigames plugin;
    private final GameManager manager;
    private final GameSettings settings;

    private final List<UUID> order = new ArrayList<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Map<UUID, PlayerSnapshot> snaps = new HashMap<>();
    private final Map<UUID, Hit> hits = new HashMap<>();
    private final ItemPool.Roller roller = new ItemPool.Roller();
    /** Pemain yang main di ronde ini (bonus match = cuma yang seri). `order` = semua anggota match. */
    private final List<UUID> roundOrder = new ArrayList<>();
    private final Map<UUID, Integer> wins = new HashMap<>();
    private int roundNo = 0;
    private boolean bonus = false;
    private BukkitTask nextTask;

    private World world;
    private Location spectatorLoc;
    private State state = State.COUNTDOWN;
    private BukkitTask countdownTask, tickTask, endTask;
    private int nextItem;
    /** Detik sampai game mulai (siap-siap + countdown), 0 kalau udah jalan. Buat HUD. */
    private int readyLeft;
    private int elapsed;
    private boolean shrinking;
    private long periodStart;
    private BossBar bossBar;
    private BukkitTask hudTask;
    private PofMap map, lastMap;
    private int lavaY = FLOOR_Y;
    /** Detik (sejak mulai) saat lava terakhir naik. */
    private int lavaLast;
    /** Radius lantai bulat (block). */
    private int floorRadius;
    private final List<BukkitTask> lavaTasks = new ArrayList<>();
    private final Map<UUID, Component> heads = new HashMap<>();

    public PofGame(ZetMinigames plugin, GameManager manager, List<Player> players, GameSettings settings) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
        for (Player p : players) {
            order.add(p.getUniqueId());
            names.put(p.getUniqueId(), p.getName());
        }
    }

    public List<UUID> playerIds() { return order; }
    public State state() { return state; }
    public Location spectatorLoc() { return spectatorLoc; }
    public boolean isTestMode() { return settings.testMode; }
    public int rounds() { return settings.rounds; }

    /** MODE TES: lihat layar SERI (pakai kepala lu sendiri), lalu match selesai. */
    public void testDraw(Player p) {
        if (!settings.testMode || state != State.RUNNING) return;
        state = State.ENDED;
        cancelTasks();
        hideHud();
        for (Player q : participants()) q.sendActionBar(Component.empty());
        showDraw(List.of(p.getUniqueId()));
    }

    /** Cuma buat MODE TES: paksa game selesai dengan pemain ini sebagai pemenang (buat lihat layar menang). */
    public void testFinish(Player p) {
        if (!settings.testMode || state != State.RUNNING) return;
        UUID id = p.getUniqueId();
        UUID w = alive.contains(id) ? id : (alive.isEmpty() ? null : alive.iterator().next());
        finish(w);
    }

    // ===================== START =====================

    public boolean begin() {
        roundNo = 1;
        bonus = false;
        roundOrder.clear();
        roundOrder.addAll(order);
        // HUD custom (bossbar + font resource pack), dibuat sekali untuk seluruh match
        for (Player p : participants()) heads.put(p.getUniqueId(), Hud.head(p));
        bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        return startRound();
    }

    /** Bikin world baru + teleport pemain + countdown. Dipakai tiap ronde. */
    private boolean startRound() {
        int n = Math.max(roundOrder.size(), settings.testMode ? settings.testPillars : 0);
        if (n < 2) n = 2;

        WorldCreator wc = new WorldCreator("pof_" + Long.toString(System.currentTimeMillis(), 36));
        wc.generator(new VoidGenerator());
        wc.generateStructures(false);
        for (BukkitTask t : lavaTasks) t.cancel();
        lavaTasks.clear();
        World prev = world;
        world = wc.createWorld();
        if (world == null) { world = prev; return false; }
        world.setTime(6000L);
        world.setStorm(false);
        world.setSpawnLocation(0, TOP_Y + 1, 0);

        // Pilar: cincin luar (maks 10) + sisanya di dalam, semua berjarak >= PILLAR_SPACING (lihat PillarLayout).
        List<int[]> pillars = PillarLayout.compute(n, PILLAR_SPACING, ThreadLocalRandom.current().nextLong());
        double radius = PillarLayout.maxRadius(pillars);
        int maxAbs = 0;
        List<Location> spawns = new ArrayList<>();
        for (int[] pl : pillars) {
            int x = pl[0];
            int z = pl[1];
            // Hadap ke tengah
            float yaw = (float) Math.toDegrees(Math.atan2(x, -z));
            spawns.add(new Location(world, x + 0.5, TOP_Y + 1, z + 0.5, yaw, 0f));
            maxAbs = Math.max(maxAbs, Math.max(Math.abs(x), Math.abs(z)));
        }
        Collections.shuffle(spawns);   // posisi tiap pemain diacak tiap ronde (luar / dalam adil)
        spectatorLoc = new Location(world, 0.5, TOP_Y + 10, 0.5);

        // Map (lantai) + pilar dari lantai ke atas
        map = chooseMap();
        floorRadius = (int) Math.ceil(radius) + BORDER_MARGIN;
        buildFloor(map, floorRadius);
        for (int[] pl : pillars) {
            for (int y = FLOOR_Y; y <= TOP_Y; y++) {
                world.getBlockAt(pl[0], y, pl[1]).setType(Material.BEDROCK, false);
            }
        }

        // Border
        WorldBorder wb = world.getWorldBorder();
        wb.setCenter(0.5, 0.5);
        wb.setSize(2.0 * (maxAbs + BORDER_MARGIN) + 1.0);
        wb.setDamageBuffer(0.0);
        wb.setDamageAmount(2.0);
        wb.setWarningDistance(3);

        // Reset status ronde
        alive.clear();
        hits.clear();
        shrinking = false;
        lavaY = FLOOR_Y;
        lavaLast = 0;   // lava langsung jalan begitu countdown kelar (naik pertama di detik ke-25)
        elapsed = 0;

        // Teleport pemain yang main ronde ini; anggota lain (bonus match) jadi penonton
        for (int i = 0; i < roundOrder.size(); i++) {
            Player p = Bukkit.getPlayer(roundOrder.get(i));
            if (p == null) continue;
            snaps.computeIfAbsent(p.getUniqueId(), k -> new PlayerSnapshot(p));
            alive.add(p.getUniqueId());
            prepare(p);
            p.teleport(spawns.get(i));
        }
        for (UUID id : order) {
            if (roundOrder.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            snaps.computeIfAbsent(id, k -> new PlayerSnapshot(p));
            p.getInventory().clear();
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(spectatorLoc);
        }

        for (Player p : participants()) {
            if (plugin.packReady(p)) p.showBossBar(bossBar);
        }
        nextItem = settings.itemIntervalSec;
        periodStart = System.currentTimeMillis();
        updateHud();
        hudTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateHud, 5L, 5L);

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

        if (prev != null) {   // world ronde sebelumnya dihapus setelah semua pemain pindah
            Bukkit.getScheduler().runTaskLater(plugin, () -> deleteWorld(prev), 40L);
        }
        return true;
    }

    private String roundLabel() {
        if (bonus) return "BONUS";
        return settings.rounds > 1 ? "R" + roundNo + "/" + settings.rounds : "";
    }

    private String roundSubtitle() {
        if (bonus) return "BONUS MATCH";
        String r = settings.rounds > 1 ? "Ronde " + roundNo + "/" + settings.rounds : "Siap-siap!";
        return map != null ? r + " | " + map.label : r;
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
        readyLeft = 0;
        elapsed = 0;
        nextItem = settings.itemIntervalSec;
        periodStart = System.currentTimeMillis();
        for (Player p : participants()) {
            CustomTitle.show(plugin, p, CustomTitle.banner(CustomTitle.BANNER_GO),
                    Component.text("MULAI!", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("Bunuh semua", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ofMillis(200)));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1f);
        }
        if (map == PofMap.LAVA) msg(Component.text("Lava mulai naik!", NamedTextColor.RED, TextDecoration.BOLD));
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ===================== TICK (1 detik) =====================

    private void tick() {
        if (state != State.RUNNING) return;
        elapsed++;
        nextItem--;
        if (nextItem <= 0) {
            giveItems();
            nextItem = settings.itemIntervalSec;
        periodStart = System.currentTimeMillis();
        }
        if (!shrinking && elapsed >= settings.shrinkStartSec) {
            shrinking = true;
            world.getWorldBorder().setSize(FINAL_BORDER_SIZE, settings.speed.durationSec);
            msg(Component.text("Border mengecil!", NamedTextColor.RED, TextDecoration.BOLD));
        }
        if (map == PofMap.LAVA) tickLava();
    }

    private PofMap chooseMap() {
        if (settings.map != null) { lastMap = settings.map; return settings.map; }
        PofMap[] all = PofMap.values();
        PofMap m;
        do {
            m = all[ThreadLocalRandom.current().nextInt(all.length)];
        } while (m == lastMap && all.length > 1);
        lastMap = m;
        return m;
    }

    private void buildFloor(PofMap m, int half) {
        if (m == PofMap.VOID) return;
        if (m == PofMap.LAVA) {
            // Danau lava: dasar bedrock (biar lava gak netes ke void) + 1 lapis lava di FLOOR_Y.
            // Lapisan berikutnya ditambah pelan-pelan oleh tickLava().
            for (int x = -half - 1; x <= half + 1; x++) {
                for (int z = -half - 1; z <= half + 1; z++) {
                    int d2 = x * x + z * z;
                    if (d2 > (half + 1) * (half + 1)) continue;
                    set(x, FLOOR_Y - 1, z, Material.BEDROCK);
                    if (d2 <= half * half) set(x, FLOOR_Y, z, Material.LAVA);
                }
            }
            return;
        }
        // Lantai bulat, 1 lapis block aja (di bawahnya void)
        Material mat = switch (m) {
            case SLIME -> Material.SLIME_BLOCK;
            case MAGMA -> Material.MAGMA_BLOCK;
            case GRASS -> Material.GRASS_BLOCK;
            case COBWEB -> Material.COBWEB;
            case TNT -> Material.TNT;
            case SNOW -> Material.POWDER_SNOW;
            default -> Material.STONE;
        };
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                if (x * x + z * z > half * half) continue;
                set(x, FLOOR_Y, z, mat);
            }
        }
    }

    private void set(int x, int y, int z, Material m) {
        world.getBlockAt(x, y, z).setType(m, false);
    }

    // ===================== LAVA RISING =====================

    /**
     * Jadwal lava (detik sejak game mulai): {jeda detik, jumlah block sekali naik}.
     * 0-2 menit: tiap 25s naik 1 | 2-3 menit: tiap 20s naik 2 | 3-5 menit: tiap 15s naik 3 | 5 menit dst: tiap 10s naik 5.
     */
    static int[] lavaStep(int sec) {
        if (sec < 120) return new int[]{25, 1};
        if (sec < 180) return new int[]{20, 2};
        if (sec < 300) return new int[]{15, 3};
        return new int[]{10, 5};
    }

    static int lavaInterval(int sec) {
        return lavaStep(sec)[0];
    }

    /** Lava naik terus sampai batas atas world (limit build). */
    private int lavaMaxY() {
        return world != null ? world.getMaxHeight() - 1 : TOP_Y + LAVA_ABOVE_TOP;
    }

    private void tickLava() {
        if (lavaY >= lavaMaxY()) return;
        int[] step = lavaStep(elapsed);
        if (elapsed - lavaLast < step[0]) return;
        lavaLast = elapsed;
        int from = lavaY + 1;
        lavaY = Math.min(lavaMaxY(), lavaY + step[1]);
        fillLayers(world, from, lavaY);
        for (Player p : participants()) p.playSound(p.getLocation(), Sound.BLOCK_LAVA_POP, 1f, 0.8f);
    }

    /** Detik sampai lava naik lagi; -1 kalau udah mentok. */
    private int lavaNextIn() {
        if (lavaY >= lavaMaxY()) return -1;
        return Math.max(0, lavaLast + lavaInterval(elapsed) - elapsed);
    }

    /** Isi lapisan lava dari y1 sampai y2 (cuma block kosong), dicicil beberapa tick biar server gak lag. */
    private void fillLayers(World w, int y1, int y2) {
        if (w == null || y2 < y1) return;
        final int r = floorRadius;
        final int layers = y2 - y1 + 1;
        final int cols = Math.max(1, LAVA_BLOCKS_PER_TICK / (layers * (2 * r + 1)));
        final int[] nextX = {-r};
        BukkitTask[] self = new BukkitTask[1];
        self[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (w != world || state == State.ENDED) {
                self[0].cancel();
                return;
            }
            int end = Math.min(r, nextX[0] + cols - 1);
            for (int x = nextX[0]; x <= end; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > r * r) continue;
                    for (int y = y1; y <= y2; y++) {
                        Block b = w.getBlockAt(x, y, z);
                        if (b.getType().isAir()) b.setType(Material.LAVA, false);
                    }
                }
            }
            nextX[0] = end + 1;
            if (nextX[0] > r) self[0].cancel();
        }, 0L, 1L);
        lavaTasks.add(self[0]);
    }

    private void giveItems() {
        for (UUID id : alive) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            ItemStack it = roller.next(id);
            Map<Integer, ItemStack> left = p.getInventory().addItem(it);
            for (ItemStack l : left.values()) p.getWorld().dropItem(p.getLocation(), l);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        }
    }

    /**
     * HUD bawah (di atas hotbar) buat map Lava Rising: sisa detik sampai lava naik + jumlah lapisan.
     * Dikirim lewat actionbar tapi isinya glyph font custom (butuh pack), jadi pemain tanpa pack gak dapat apa-apa.
     */
    private void sendLavaBar() {
        if (map != PofMap.LAVA || state != State.RUNNING) return;
        int in = lavaNextIn();
        String t = in < 0 ? "LAVA MAX" : "LAVA +" + lavaStep(elapsed)[1] + "  " + Hud.clock(in);
        int color = in < 0 ? 0xFF5555 : in <= 5 ? 0xFFD23C : 0xFF8A3C;
        Component bar = Hud.buildBottom(t, color);
        for (Player p : participants()) {
            if (plugin.packReady(p)) p.sendActionBar(bar);
        }
    }

    /** Update HUD custom (dipanggil 4x per detik). */
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
            frac = (System.currentTimeMillis() - periodStart) / (settings.itemIntervalSec * 1000.0);
        }
        bossBar.name(Hud.build(hs, al, alive.size(), n, Math.max(0, nextItem), frac,
                plugin.headAdvance(), plugin.slotWidth(),
                Math.max(0, settings.shrinkStartSec - elapsed), shrinking, roundLabel(),
                state == State.RUNNING ? elapsed : 0,
                readyLeft > 0 ? "MULAI " + readyLeft + "s" : null));
        sendLavaBar();
    }

    private void hideHud() {
        if (bossBar == null) return;
        for (Player p : participants()) p.hideBossBar(bossBar);
    }

    // ===================== ELIMINASI & MENANG =====================

    public void eliminate(Player p, String cause) {
        UUID id = p.getUniqueId();
        if (state != State.RUNNING || !alive.remove(id)) return;

        UUID killer = recentKiller(id);
        Component line;
        if (killer != null) {
            line = Component.text(names.get(id), NamedTextColor.RED)
                    .append(Component.text(" dibunuh oleh ", NamedTextColor.GRAY))
                    .append(Component.text(names.getOrDefault(killer, "?"), NamedTextColor.GREEN));
        } else {
            line = Component.text(names.get(id), NamedTextColor.RED)
                    .append(Component.text(" " + cause, NamedTextColor.GRAY));
        }
        msg(line.append(Component.text("  (" + alive.size() + " sisa)", NamedTextColor.DARK_GRAY)));

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || p.isDead()) return;
            p.getInventory().clear();
            p.setHealth(20.0);
            p.setFireTicks(0);
            p.setFallDistance(0f);
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(spectatorLoc);
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

    // ===================== AKHIR RONDE / MATCH =====================

    private void finish(UUID winner) {
        if (state == State.ENDED) return;
        state = State.ENDED;
        cancelTasks();
        hideHud();
        for (Player p : participants()) p.sendActionBar(Component.empty());

        String doneLabel = bonus ? "BONUS MATCH"
                : "RONDE " + roundNo + (settings.rounds > 1 ? "/" + settings.rounds : "");

        if (bonus) {                                   // bonus match: pemenangnya langsung juara
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

        // seri & opsi SERI mati => bonus match antar pemain yang seri
        bonus = true;
        roundOrder.clear();
        roundOrder.addAll(top);
        showRound(doneLabel, winner, "Seri: " + joinNames(top) + ". BONUS MATCH!");
        scheduleNext();
    }

    /** Pemenang udah pasti walau semua sisa ronde dimenangin pemain lain? */
    private boolean clinched(int remaining) {
        int lead = 0, second = 0;
        for (UUID id : order) {
            int w = wins.getOrDefault(id, 0);
            if (w > lead) { second = lead; lead = w; }
            else if (w > second) second = w;
        }
        return lead > second + remaining;
    }

    /** Pemain dengan win terbanyak (kosong kalau belum ada yang menang sama sekali). */
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
            roundNo++;
        } else {
            roundOrder.removeIf(id -> !order.contains(id));
        }
        if (!settings.testMode && roundOrder.size() < 2) {
            if (roundOrder.size() == 1) showChampion(roundOrder.get(0)); else showNone();
            return;
        }
        if (!startRound()) {
            msg(Component.text("Gagal bikin world.", NamedTextColor.RED));
            showNone();
        }
    }

    /** Layar pemenang satu ronde (match masih lanjut). */
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

    /** Layar juara (match selesai). */
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

    /** Layar SERI (opsi seri aktif dan win terbanyak sama). */
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

    /** Match selesai tanpa pemenang. */
    private void showNone() {
        Title.Times times = Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(6), Duration.ofMillis(800));
        for (Player p : participants()) {
            p.showTitle(Title.title(Component.text("GAME SELESAI", NamedTextColor.GRAY, TextDecoration.BOLD),
                    Component.text("Gak ada pemenang", NamedTextColor.DARK_GRAY), times));
        }
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, 160L);
    }

    /** Kembang api kecil di atas pemenang. */
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

    /** Dipanggil saat plugin disable / server stop. Sinkron. */
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
        for (BukkitTask t : lavaTasks) t.cancel();
        lavaTasks.clear();
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

    // ===================== EVENT HANDLER (dipanggil GameListener) =====================

    public void onDamage(Player victim, EntityDamageEvent e) {
        UUID id = victim.getUniqueId();
        if (state != State.RUNNING || !alive.contains(id)) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent be) {
            Player a = attackerOf(be.getDamager());
            if (a != null && !a.equals(victim)) {
                if (!alive.contains(a.getUniqueId())) {
                    e.setCancelled(true);
                    return;
                }
                hits.put(id, new Hit(a.getUniqueId(), System.currentTimeMillis()));
            }
        }
        if (e.getFinalDamage() >= victim.getHealth()) {
            e.setCancelled(true);
            eliminate(victim, switch (e.getCause()) {
                case VOID -> "jatuh ke void";
                case LAVA, FIRE, FIRE_TICK -> "kena lava/api";
                case FALL -> "jatuh";
                case HOT_FLOOR -> "kena magma";
                case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "kena ledakan";
                default -> "tereliminasi";
            });
        }
    }

    public void onMove(Player p, PlayerMoveEvent e) {
        if (state == State.COUNTDOWN) {
            Location from = e.getFrom();
            Location to = e.getTo();
            if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
                e.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                        to.getYaw(), to.getPitch()));
            }
        } else if (state == State.RUNNING && alive.contains(p.getUniqueId())) {
            Location to = e.getTo();
            if (to != null && to.getY() < FLOOR_Y - 25) {
                eliminate(p, "jatuh ke void");
            }
        }
    }

    public void onBlockPlace(BlockPlaceEvent e) {
        if (state != State.RUNNING) e.setCancelled(true);
    }

    public void onBlockBreak(BlockBreakEvent e) {
        if (state != State.RUNNING) e.setCancelled(true);
    }

    public void onDeath(Player p, PlayerDeathEvent e) {
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        eliminate(p, "mati");
    }

    public void onRespawn(Player p, PlayerRespawnEvent e) {
        if (spectatorLoc == null) return;
        e.setRespawnLocation(spectatorLoc);
        if (state == State.ENDED) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            p.getInventory().clear();
            p.setGameMode(GameMode.SPECTATOR);
        });
    }

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

    public void forfeit(Player p) {
        if (state == State.RUNNING && alive.contains(p.getUniqueId())) {
            eliminate(p, "nyerah");
        }
    }

    // ===================== UTIL =====================

    private Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (damager instanceof TNTPrimed t && t.getSource() instanceof Player p) return p;
        return null;
    }

    private UUID recentKiller(UUID victim) {
        Hit h = hits.get(victim);
        if (h != null && System.currentTimeMillis() - h.at() < 10_000L) return h.attacker();
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
