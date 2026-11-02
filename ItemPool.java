package id.zetminigames.pof;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.MusicInstrument;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pool item Pillar of Fortune:
 * - 60% roll dari pool PVP (senjata, armor, pearl, wind charge, gapple, dll)
 * - 40% roll dari SEMUA material valid (random murni)
 * - Jumlah selalu 1, item sama gak boleh berturut-turut.
 */
public final class ItemPool {
    static final int MAX_BLOCK = 1;
    static final int MAX_ITEM = 1;
    static final int GAP_BETWEEN = 1;
    private static final int MAX_BLOCK_RUN = 4;

    /** Peluang ambil dari pool PVP (sisanya = random all). */
    static final double PVP_CHANCE = 0.60;

    private static final Set<String> BLACKLIST = new HashSet<>(List.of(
            "BARRIER", "LIGHT", "STRUCTURE_VOID", "STRUCTURE_BLOCK", "JIGSAW",
            "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART",
            "DEBUG_STICK", "KNOWLEDGE_BOOK", "TEST_BLOCK", "TEST_INSTANCE_BLOCK",
            "SPAWNER", "TRIAL_SPAWNER", "VAULT", "REINFORCED_DEEPSLATE", "FARMLAND"));

    private static boolean pvp(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_SPEAR")
                || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
                || n.equals("BOW") || n.equals("CROSSBOW") || n.equals("MACE")
                || n.equals("TRIDENT") || n.equals("SHIELD")
                || n.equals("ENDER_PEARL") || n.equals("WIND_CHARGE")
                || n.equals("GOLDEN_APPLE") || n.equals("ENCHANTED_GOLDEN_APPLE")
                || n.equals("TOTEM_OF_UNDYING") || n.equals("COBWEB")
                || n.equals("ARROW") || n.equals("SPECTRAL_ARROW") || n.equals("TIPPED_ARROW")
                || n.equals("TNT") || n.equals("END_CRYSTAL")
                || n.equals("FIRE_CHARGE") || n.equals("FLINT_AND_STEEL")
                || n.equals("WATER_BUCKET") || n.equals("LAVA_BUCKET")
                || n.equals("POTION") || n.equals("SPLASH_POTION") || n.equals("LINGERING_POTION")
                || n.equals("SNOWBALL") || n.equals("EGG")
                || n.equals("FISHING_ROD") || n.equals("SHIELD");
    }

    private record Entry(Material mat, boolean block) {}

    private static final List<Entry> ALL = new ArrayList<>();
    private static final List<Entry> PVP = new ArrayList<>();
    private static final List<PotionType> POTIONS = new ArrayList<>();
    private static final List<Enchantment> ENCHANTS = new ArrayList<>();
    private static final List<MusicInstrument> HORNS = new ArrayList<>();

    static {
        for (PotionType t : PotionType.values()) {
            String n = t.name();
            if (n.equals("WATER") || n.equals("MUNDANE") || n.equals("THICK") || n.equals("AWKWARD")) continue;
            POTIONS.add(t);
        }
        try { Registry.ENCHANTMENT.forEach(ENCHANTS::add); } catch (Throwable ignored) { }
        try { Registry.INSTRUMENT.forEach(HORNS::add); } catch (Throwable ignored) { }

        for (Material m : Material.values()) {
            if (!valid(m)) continue;
            Entry e = new Entry(m, m.isBlock());
            ALL.add(e);
            if (pvp(m)) PVP.add(e);
        }
    }

    private ItemPool() {}

    private static boolean valid(Material m) {
        if (m.isLegacy() || m.isAir() || !m.isItem()) return false;
        return !BLACKLIST.contains(m.name());
    }

    private static ItemStack make(Entry e) {
        ItemStack it = new ItemStack(e.mat, 1);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        ItemMeta meta = it.getItemMeta();
        try {
            switch (e.mat) {
                case POTION, SPLASH_POTION, LINGERING_POTION, TIPPED_ARROW -> {
                    if (meta instanceof PotionMeta pm && !POTIONS.isEmpty()) {
                        pm.setBasePotionType(POTIONS.get(r.nextInt(POTIONS.size())));
                        it.setItemMeta(pm);
                    }
                }
                case ENCHANTED_BOOK -> {
                    if (meta instanceof EnchantmentStorageMeta em && !ENCHANTS.isEmpty()) {
                        Enchantment en = ENCHANTS.get(r.nextInt(ENCHANTS.size()));
                        em.addStoredEnchant(en, r.nextInt(1, en.getMaxLevel() + 1), true);
                        it.setItemMeta(em);
                    }
                }
                case PLAYER_HEAD -> {
                    if (meta instanceof SkullMeta sm) {
                        Collection<? extends Player> on = Bukkit.getOnlinePlayers();
                        if (!on.isEmpty()) {
                            Player p = new ArrayList<Player>(on).get(r.nextInt(on.size()));
                            sm.setOwningPlayer((OfflinePlayer) p);
                            it.setItemMeta(sm);
                        }
                    }
                }
                case GOAT_HORN -> {
                    if (meta instanceof MusicInstrumentMeta mm && !HORNS.isEmpty()) {
                        mm.setInstrument(HORNS.get(r.nextInt(HORNS.size())));
                        it.setItemMeta(mm);
                    }
                }
                default -> { }
            }
        } catch (Throwable ignored) { }
        return it;
    }

    public static final class Roller {
        private static final class State {
            final Map<Material, Integer> last = new HashMap<>();
            int draws = 0;
            int blockRun = 0;
        }

        private final Map<UUID, State> states = new HashMap<>();

        public ItemStack next(UUID id) {
            State s = states.computeIfAbsent(id, k -> new State());
            s.draws++;

            ThreadLocalRandom r = ThreadLocalRandom.current();
            // 60% PVP pool, 40% full random
            List<Entry> preferred = r.nextDouble() < PVP_CHANCE ? PVP : ALL;

            List<Entry> ok = filter(preferred, s);
            if (ok.isEmpty()) ok = filter(ALL, s);
            if (ok.isEmpty()) ok = ALL;

            Entry pick = ok.get(r.nextInt(ok.size()));
            s.last.put(pick.mat, s.draws);
            if (pick.block) s.blockRun++;
            else s.blockRun = 0;
            return make(pick);
        }

        private static List<Entry> filter(List<Entry> src, State s) {
            List<Entry> ok = new ArrayList<>();
            for (Entry e : src) {
                Integer l = s.last.get(e.mat);
                if (l != null && s.draws - l <= GAP_BETWEEN) continue;
                if (e.block && s.blockRun >= MAX_BLOCK_RUN) continue;
                ok.add(e);
            }
            return ok;
        }
    }
}
