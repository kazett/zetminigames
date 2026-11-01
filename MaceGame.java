package id.zetminigames.mace;

import id.zetminigames.hud.CustomTitle;
import id.zetminigames.core.GameManager;
import id.zetminigames.core.GameSettings;
import id.zetminigames.hud.Hud;
import id.zetminigames.core.Match;
import id.zetminigames.core.PlayerSnapshot;
import id.zetminigames.hud.WinScreen;
import id.zetminigames.ZetMinigames;

import id.zetminigames.pof.VoidGenerator;
import org.bukkit.Material;
import org.bukkit.WorldBorder;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class MaceGame implements Match {
    private enum State { LOADING, COUNTDOWN, RUNNING, ENDED }
    private enum Phase { INTRO, SPIN, READY, ATTEMPT, RESOLVE }

    private static final int PLATFORM_Y = 100;
    private static final int PLATFORM_R = 14;
    private static final int COUNTDOWN_SEC = 5;
    private static final int READY_SEC = 3;
    private static final int SPIN_TICKS = 70;
    private static final double RING_SPACING = 2.5;
    /** Sprite mace miring 45 derajat: kepala mace nunjuk ke yaw (rotasi - 135). 135 bikin kepala nunjuk pas ke pemain. Ubah kalau masih meleset. */
    private static final float POINT_OFFSET = 135f;

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
    private final List<Location> slotLocs = new ArrayList<>();
    private final List<BukkitTask> temp = new ArrayList<>();
    private int roundNo = 0;
    private boolean bonus = false;
    private BukkitTask nextTask;

    private World world;
    private Location spectatorLoc;
    private State state = State.LOADING;
    private Phase phase = Phase.INTRO;
    private BukkitTask tickTask, endTask, hudTask;
    private BossBar bossBar;
    private ItemDisplay spin;
    private ArmorStand dummy;

    private UUID holder;
    private boolean killed;
    private long deadline, phaseStart;

    public MaceGame(ZetMinigames plugin, GameManager manager, List<Player> players, GameSettings settings) {
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

    private BukkitTask later(long ticks, Runnable r) {
        BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, r, ticks);
        temp.add(t);
        return t;
    }

    // ===================== WORLD =====================

    private boolean startRound() {
        int slots = Math.max(roundOrder.size(), settings.testMode ? 2 : 0);
        if (slots < 2) slots = 2;
        double ringR = Math.max(3.0, RING_SPACING / (2.0 * Math.sin(Math.PI / slots)));

        WorldCreator wc = new WorldCreator("pof_mace_" + Long.toString(System.currentTimeMillis(), 36));
        wc.generator(new VoidGenerator());
        wc.generateStructures(false);
        World created = wc.createWorld();
        if (created == null) return false;
        World prev = world;
        world = created;
        world.setTime(6000L);
        world.setStorm(false);
        world.setSpawnLocation(0, PLATFORM_Y + 1, 0);

        // Platform bulat
        for (int dx = -PLATFORM_R; dx <= PLATFORM_R; dx++) {
            for (int dz = -PLATFORM_R; dz <= PLATFORM_R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > PLATFORM_R + 0.5) continue;
                Material m = Math.abs(d - ringR) < 0.6 ? Material.GOLD_BLOCK : Material.SMOOTH_STONE;
                world.getBlockAt(dx, PLATFORM_Y, dz).setType(m, false);
            }
        }
        WorldBorder wb = world.getWorldBorder();
        wb.setCenter(0.5, 0.5);
        wb.setSize(2.0 * (PLATFORM_R + 8) + 1.0);

        // Slot pemain ngelilingin tengah, hadap tengah
        slotLocs.clear();
        for (int i = 0; i < slots; i++) {
            double ang = 2.0 * Math.PI * i / slots;
            double dx = ringR * Math.cos(ang);
            double dz = ringR * Math.sin(ang);
            float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
            slotLocs.add(new Location(world, 0.5 + dx, PLATFORM_Y + 1, 0.5 + dz, yaw, 0f));
        }
        spectatorLoc = new Location(world, 0.5, PLATFORM_Y + 8, 0.5);

        alive.clear();
        deathLocs.clear();
        holder = null;
        phase = Phase.INTRO;
        state = State.COUNTDOWN;

        for (int i = 0; i < roundOrder.size(); i++) {
            Player p = Bukkit.getPlayer(roundOrder.get(i));
            if (p == null) continue;
            snaps.computeIfAbsent(p.getUniqueId(), k -> new PlayerSnapshot(p));
            alive.add(p.getUniqueId());
            prepare(p);
            p.teleport(slotLocs.get(i));
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

        spawnSpin();
        dummy = null;
        if (settings.testMode && roundOrder.size() < 2) {
            Location dl = slotLocs.get(1);
            dummy = world.spawn(dl, ArmorStand.class, a -> {
                a.setArms(true);
                a.setBasePlate(false);
                a.customName(Component.text("DUMMY", NamedTextColor.RED));
                a.setCustomNameVisible(true);
            });
        }

        for (Player p : participants()) {
            if (plugin.packReady(p)) p.showBossBar(bossBar);
        }
        updateHud();
        hudTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateHud, 5L, 5L);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);

        countdown(COUNTDOWN_SEC, this::startSpin);

        if (prev != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> deleteWorld(prev), 20L);
        }
        return true;
    }

    private void spawnSpin() {
        Location c = new Location(world, 0.5, PLATFORM_Y + 1.6, 0.5);
        spin = world.spawn(c, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.MACE));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f((float) Math.toRadians(-90), 1f, 0f, 0f),
                    new Vector3f(1.6f, 1.6f, 1.6f),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
            d.setPersistent(false);
        });
    }

    private void removeSpin() {
        if (spin != null) {
            spin.remove();
            spin = null;
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

    // ===================== ALUR =====================

    private void countdown(int from, Runnable done) {
        BukkitTask[] self = new BukkitTask[1];
        int[] c = {from};
        self[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state == State.ENDED) { self[0].cancel(); return; }
            if (c[0] > 0) {
                int n = c[0];
                for (Player p : participants()) {
                    CustomTitle.show(plugin, p, CustomTitle.count(n),
                            Component.text(String.valueOf(n), NamedTextColor.YELLOW, TextDecoration.BOLD),
                            Component.text(roundSubtitle(), NamedTextColor.GRAY),
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

    /** Roulette: mace muter lalu berhenti ngarah ke 1 pemain. */
    private void startSpin() {
        if (state == State.ENDED) return;
        state = State.RUNNING;
        phase = Phase.SPIN;
        holder = null;
        killed = false;

        List<UUID> cand = new ArrayList<>();
        for (UUID id : alive) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) cand.add(id);
        }
        if (cand.isEmpty() || spin == null) { checkWin(); return; }
        UUID pick = cand.get(ThreadLocalRandom.current().nextInt(cand.size()));
        Location sl = slotLocs.get(Math.max(0, roundOrder.indexOf(pick)));
        double dx = sl.getX() - 0.5;
        double dz = sl.getZ() - 0.5;
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + POINT_OFFSET;
        float startYaw = spin.getLocation().getYaw();
        float delta = (((targetYaw - startYaw) % 360f) + 360f) % 360f;
        float total = 720f + delta;

        int[] t = {0};
        BukkitTask[] self = new BukkitTask[1];
        self[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state == State.ENDED || spin == null || phase != Phase.SPIN) { self[0].cancel(); return; }
            t[0]++;
            double k = Math.min(1.0, (double) t[0] / SPIN_TICKS);
            double ease = 1.0 - Math.pow(1.0 - k, 3);
            spin.setRotation(startYaw + (float) (total * ease), 0f);
            if (t[0] % 3 == 0) {
                for (Player p : participants()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, (float) (0.7 + 1.0 * k));
                }
            }
            if (t[0] >= SPIN_TICKS) {
                self[0].cancel();
                giveMace(pick);
            }
        }, 0L, 1L);
        temp.add(self[0]);
        updateHud();
    }

    private void giveMace(UUID pick) {
        if (state == State.ENDED || phase != Phase.SPIN) return;
        Player p = Bukkit.getPlayer(pick);
        if (p == null || !p.isOnline() || !alive.contains(pick)) {
            later(20L, this::startSpin);
            return;
        }
        holder = pick;
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();
        meta.addEnchant(Enchantment.BREACH, 4, true);
        meta.setUnbreakable(true);
        mace.setItemMeta(meta);
        p.getInventory().clear();
        p.getInventory().setItem(0, mace);
        p.getInventory().setItem(1, new ItemStack(Material.WIND_CHARGE, 1));
        p.getInventory().setHeldItemSlot(0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 120, 0, false, false));

        msg(Component.text(names.get(pick), NamedTextColor.YELLOW, TextDecoration.BOLD)
                .append(Component.text(" dapat MACE", NamedTextColor.GRAY)));
        Title.Times times = Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(300));
        for (Player q : participants()) {
            q.showTitle(Title.title(Component.text(names.get(pick), NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text("dapat MACE", NamedTextColor.GRAY), times));
            q.playSound(q.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        phase = Phase.READY;
        updateHud();
        countdown(READY_SEC, this::startAttempt);
    }

    private void startAttempt() {
        if (state == State.ENDED || phase != Phase.READY) return;
        phase = Phase.ATTEMPT;
        long now = System.currentTimeMillis();
        phaseStart = now;
        deadline = now + settings.maceSec * 1000L;
        for (Player p : participants()) {
            boolean h = p.getUniqueId().equals(holder);
            CustomTitle.show(plugin, p, CustomTitle.banner(CustomTitle.BANNER_GO),
                    Component.text("MULAI!", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text(h ? "Bunuh!" : "Hindari!", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ofMillis(200)));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1f);
        }
        updateHud();
    }

    private void tick() {
        if (state != State.RUNNING) return;
        if (phase == Phase.ATTEMPT && System.currentTimeMillis() >= deadline) {
            resolve(killed ? "Waktu habis" : "Waktu habis, gak ada yang mati");
        }
        sendHud();
    }

    /** Selesaiin percobaan: balik ke lingkaran, roulette lagi. */
    private void resolve(String why) {
        if (state != State.RUNNING || phase == Phase.RESOLVE) return;
        phase = Phase.RESOLVE;
        if (why != null) msg(Component.text(why, NamedTextColor.GRAY));
        later(40L, this::resetRing);
    }

    private void resetRing() {
        if (state != State.RUNNING) return;
        for (Player p : participants()) {
            p.removePotionEffect(PotionEffectType.GLOWING);
        }
        for (int i = 0; i < roundOrder.size(); i++) {
            UUID id = roundOrder.get(i);
            if (!alive.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            p.getInventory().clear();
            p.setHealth(20.0);
            p.setFireTicks(0);
            p.setFallDistance(0f);
            p.setVelocity(new Vector(0, 0, 0));
            p.teleport(slotLocs.get(i));
        }
        holder = null;
        phase = Phase.INTRO;
        updateHud();
        later(30L, this::startSpin);
    }

    // ===================== HUD =====================

    private String topText() {
        return switch (phase) {
            case INTRO, READY -> "SIAP";
            case SPIN -> "ROULETTE";
            case ATTEMPT -> "MACE " + Math.max(0, (int) Math.ceil((deadline - System.currentTimeMillis()) / 1000.0)) + "s";
            case RESOLVE -> "RESET";
        };
    }

    private void sendHud() {
        Component full = Component.text(topText(), NamedTextColor.YELLOW)
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(alive.size() + "/" + roundOrder.size(), NamedTextColor.GREEN));
        for (Player p : participants()) {
            if (!plugin.packReady(p)) p.sendActionBar(full);
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
        if (state == State.RUNNING && phase == Phase.ATTEMPT) {
            frac = (System.currentTimeMillis() - phaseStart) / (double) Math.max(1L, deadline - phaseStart);
        }
        bossBar.name(Hud.buildMace(hs, al, alive.size(), n, topText(), frac,
                plugin.headAdvance(), plugin.slotWidth(), roundLabel()));
    }

    private void hideHud() {
        if (bossBar == null) return;
        for (Player p : participants()) p.hideBossBar(bossBar);
    }

    // ===================== ELIMINASI =====================

    public void eliminate(Player p, String cause) {
        UUID id = p.getUniqueId();
        if (state != State.RUNNING || !alive.remove(id)) return;

        Component line = Component.text(names.get(id), NamedTextColor.RED)
                .append(Component.text(" " + cause, NamedTextColor.GRAY))
                .append(Component.text("  (" + alive.size() + " sisa)", NamedTextColor.DARK_GRAY));
        msg(line);

        // Percobaan jalan terus sampai timer abis. Kalau pemegang mace-nya sendiri yang mati, langsung kelar.
        if (phase == Phase.ATTEMPT) {
            killed = true;
            if (id.equals(holder)) later(30L, () -> resolve(null));
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || p.isDead()) return;
            p.getInventory().clear();
            p.setHealth(20.0);
            p.setFireTicks(0);
            p.setFallDistance(0f);
            p.removePotionEffect(PotionEffectType.GLOWING);
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

    private void finish(UUID winner) {
        if (state == State.ENDED) return;
        state = State.ENDED;
        cancelTasks();
        hideHud();
        for (Player p : participants()) p.sendActionBar(Component.empty());

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
        if (tickTask != null) tickTask.cancel();
        if (hudTask != null) hudTask.cancel();
        for (BukkitTask t : new ArrayList<>(temp)) t.cancel();
        temp.clear();
        removeSpin();
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

    // ===================== EVENT =====================

    @Override
    public void onDamage(Player victim, EntityDamageEvent e) {
        UUID id = victim.getUniqueId();
        if (state != State.RUNNING || !alive.contains(id)) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            eliminate(victim, "jatuh ke void");
            return;
        }
        boolean live = phase == Phase.ATTEMPT;
        if (live && e instanceof EntityDamageByEntityEvent be && be.getDamager() instanceof Player a
                && a.getUniqueId().equals(holder) && !a.equals(victim)) {
            if (e.getFinalDamage() >= victim.getHealth()) {
                e.setCancelled(true);
                eliminate(victim, "kena mace");
            }
            return;
        }
        // selain pukulan mace (fall, ledakan windcharge, dll) gak bikin damage
        e.setCancelled(true);
    }

    @Override
    public void onAttack(Player attacker, EntityDamageByEntityEvent e) {
        // dummy (mode tes) gak boleh pecah
        if (settings.testMode && e.getEntity() instanceof ArmorStand) {
            e.setCancelled(true);
        }
    }

    @Override
    public void onMove(Player p, PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null) return;
        UUID id = p.getUniqueId();
        if (state == State.RUNNING && alive.contains(id)
                && (phase == Phase.ATTEMPT || phase == Phase.RESOLVE) && to.getY() < PLATFORM_Y - 12) {
            eliminate(p, "jatuh ke void");
            return;
        }
        boolean frozen = alive.contains(id) && (state == State.COUNTDOWN
                || (state == State.RUNNING && (phase == Phase.INTRO || phase == Phase.SPIN
                        || (phase == Phase.READY && id.equals(holder)))));
        if (!frozen) return;
        Location from = e.getFrom();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            e.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
        }
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent e) { e.setCancelled(true); }

    @Override
    public void onBlockBreak(BlockBreakEvent e) { e.setCancelled(true); }

    @Override
    public void onDeath(Player p, PlayerDeathEvent e) {
        e.deathMessage(null);
        e.getDrops().clear();
        e.setKeepInventory(true);
        deathLocs.put(p.getUniqueId(), p.getLocation());
        eliminate(p, "mati");
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
        if (id.equals(holder) && phase != Phase.RESOLVE && state == State.RUNNING) resolve(null);
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
