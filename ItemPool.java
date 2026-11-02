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
 * Pool item Pillar of Fortune (v24b): SEMUA Material valid, peluang sama (100% random).
 * - Jumlah selalu 1 (gak pernah stack > 1).
 * - Item yang sama gak boleh berturut-turut (GAP_BETWEEN = 1).
 * - Varian acak: potion, enchanted book, player head, goat horn.
 * - Blacklist cuma item teknis yang rusak game.
 */
public final class ItemPool {
    static final int MAX_BLOCK = 1;
    static final int MAX_ITEM = 1;
    /** Minimal 1 item lain di antara dua item Material yang sama → gak berturut-turut. */
    static final int GAP_BETWEEN = 1;
    private static final int MAX_BLOCK_RUN = 8;

    private static final Set<String> BLACKLIST = new HashSet<>(List.of(
            "BARRIER", "LIGHT", "STRUCTURE_VOID", "STRUCTURE_BLOCK", "JIGSAW",
            "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART",
            "DEBUG_STICK", "KNOWLEDGE_BOOK", "TEST_BLOCK", "TEST_INSTANCE_BLOCK",
            "SPAWNER", "TRIAL_SPAWNER", "VAULT", "REINFORCED_DEEPSLATE", "FARMLAND"));

    private record Entry(Material mat, boolean block) {}

    private static final List<Entry> POOL = new ArrayList<>();
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
            POOL.add(new Entry(m, m.isBlock()));
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

    /** Pengocok per game: riwayat per pemain biar item gak berturut-turut sama. */
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

            List<Entry> ok = new ArrayList<>();
            for (Entry e : POOL) {
                Integer l = s.last.get(e.mat);
                if (l != null && s.draws - l <= GAP_BETWEEN) continue;
                if (e.block && s.blockRun >= MAX_BLOCK_RUN) continue;
                ok.add(e);
            }
            Entry pick;
            if (ok.isEmpty()) {
                pick = POOL.get(ThreadLocalRandom.current().nextInt(POOL.size()));
            } else {
                pick = ok.get(ThreadLocalRandom.current().nextInt(ok.size()));
            }

            s.last.put(pick.mat, s.draws);
            if (pick.block) s.blockRun++;
            else s.blockRun = 0;
            return make(pick);
        }
    }
}
