package id.zetminigames.pof;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Daftar item random (pakai bobot + anti-ulang per pemain).
 *
 * Aturan:
 *  - Item yang sama baru boleh keluar lagi setelah minimal GAP_BETWEEN (4) item lain di antaranya
 *    (jadi turn 1 sword, paling cepat turn 6 baru bisa sword lagi). Sesudah itu murni random.
 *  - Blok paling sering keluar (~45%), jumlah per drop maksimal MAX_BLOCK (1).
 *  - Item lain maksimal MAX_ITEM (1) per drop. Semua item non-blok peluangnya hampir sama (gak ada yang super langka).
 *  - Maksimal MAX_BLOCK_RUN blok berturut-turut biar tetap ada selang item lain.
 * Mau ubah: edit bagian static {} di bawah (bobot & jumlah).
 */
public final class ItemPool {
    static final int MAX_BLOCK = 1;
    static final int MAX_ITEM = 1;
    static final int GAP_BETWEEN = 4;       // minimal item lain di antara dua item yang sama
    private static final int MAX_BLOCK_RUN = 4;

    private record Entry(Material mat, boolean block, int min, int max, double weight,
                         Enchantment ench, int level) {}

    private static final List<Entry> POOL = new ArrayList<>();

    static {
        // ---- BLOK (jumlah 2..5) ----
        block(Material.COBBLESTONE, 6);
        block(Material.OAK_PLANKS, 6);
        block(Material.STONE, 5);
        block(Material.WHITE_WOOL, 5);
        block(Material.OAK_LOG, 4);
        block(Material.GLASS, 4);
        block(Material.SAND, 3.5);
        block(Material.COBWEB, 1, 3, 2.5);
        block(Material.SLIME_BLOCK, 1, 2, 2);
        block(Material.OBSIDIAN, 1, 2, 1.5);

        // ---- LEMPAR / TAMBAHAN (maks 3) ----
        item(Material.SNOWBALL, 2, 3, 2);
        item(Material.EGG, 2, 3, 2);
        item(Material.WIND_CHARGE, 1, 2, 1.8);
        item(Material.ENDER_PEARL, 1, 1, 1.5);
        item(Material.ARROW, 2, 3, 1.8);
        item(Material.TNT, 1, 2, 1.5);

        // ---- UTILITY ----
        item(Material.WATER_BUCKET, 1, 1, 1.8);
        item(Material.LAVA_BUCKET, 1, 1, 1.3);
        item(Material.FLINT_AND_STEEL, 1, 1, 1.3);
        item(Material.FISHING_ROD, 1, 1, 1.5);
        item(Material.SHIELD, 1, 1, 1.4);
        enchanted(Material.STICK, 1.5, Enchantment.KNOCKBACK, 3);

        // ---- MAKANAN ----
        item(Material.COOKED_BEEF, 1, 3, 2);
        item(Material.BREAD, 2, 3, 1.8);
        item(Material.GOLDEN_APPLE, 1, 1, 1.5);

        // ---- SENJATA ----
        item(Material.WOODEN_SWORD, 1, 1, 1.6);
        item(Material.STONE_SWORD, 1, 1, 1.5);
        item(Material.IRON_SWORD, 1, 1, 1.4);
        item(Material.DIAMOND_SWORD, 1, 1, 1.2);
        item(Material.BOW, 1, 1, 1.5);
        item(Material.CROSSBOW, 1, 1, 1.3);

        // ---- ARMOR ----
        item(Material.LEATHER_HELMET, 1, 1, 1.3);
        item(Material.LEATHER_BOOTS, 1, 1, 1.3);
        item(Material.CHAINMAIL_LEGGINGS, 1, 1, 1.3);
        item(Material.IRON_HELMET, 1, 1, 1.3);
        item(Material.IRON_CHESTPLATE, 1, 1, 1.3);
        item(Material.IRON_BOOTS, 1, 1, 1.3);
        item(Material.DIAMOND_HELMET, 1, 1, 1.2);

        // ---- TOOL ----
        item(Material.IRON_PICKAXE, 1, 1, 1.3);
        item(Material.IRON_AXE, 1, 1, 1.2);
    }

    private ItemPool() {}

    /** Blok biasa: jumlah 2..MAX_BLOCK. */
    private static void block(Material m, double weight) {
        block(m, 2, MAX_BLOCK, weight);
    }

    /** Blok dengan rentang jumlah sendiri (dibatasi MAX_BLOCK). */
    private static void block(Material m, int min, int max, double weight) {
        int hi = Math.min(max, MAX_BLOCK);
        POOL.add(new Entry(m, true, Math.min(min, hi), hi, weight, null, 0));
    }

    private static void item(Material m, int min, int max, double weight) {
        int hi = Math.min(max, MAX_ITEM);
        POOL.add(new Entry(m, false, Math.min(min, hi), hi, weight, null, 0));
    }

    private static void enchanted(Material m, double weight, Enchantment e, int lvl) {
        POOL.add(new Entry(m, false, 1, 1, weight, e, lvl));
    }

    private static ItemStack make(Entry e) {
        int amt = e.min == e.max ? e.min : ThreadLocalRandom.current().nextInt(e.min, e.max + 1);
        ItemStack it = new ItemStack(e.mat, amt);
        if (e.ench != null) it.addUnsafeEnchantment(e.ench, e.level);
        return it;
    }

    /** Pengocok per game: nyimpen riwayat tiap pemain biar item gak itu-itu mulu. */
    public static final class Roller {
        private static final class State {
            final int[] last = new int[POOL.size()];
            int draws = 0;
            int blockRun = 0;

            State() { Arrays.fill(last, -1000); }
        }

        private final Map<UUID, State> states = new HashMap<>();

        public ItemStack next(UUID id) {
            State s = states.computeIfAbsent(id, k -> new State());
            s.draws++;

            List<Integer> ok = new ArrayList<>();
            double total = 0;
            for (int i = 0; i < POOL.size(); i++) {
                Entry e = POOL.get(i);
                // selisih turn harus > GAP_BETWEEN => minimal 4 item lain di antaranya
                if (s.draws - s.last[i] <= GAP_BETWEEN) continue;
                if (e.block && s.blockRun >= MAX_BLOCK_RUN) continue;
                ok.add(i);
                total += e.weight;
            }
            if (ok.isEmpty()) {                       // cadangan (praktis gak pernah kejadian)
                ok.add(ThreadLocalRandom.current().nextInt(POOL.size()));
                total = POOL.get(ok.get(0)).weight;
            }

            double r = ThreadLocalRandom.current().nextDouble() * total;
            int pick = ok.get(ok.size() - 1);
            for (int i : ok) {
                r -= POOL.get(i).weight;
                if (r <= 0) { pick = i; break; }
            }

            Entry e = POOL.get(pick);
            s.last[pick] = s.draws;
            s.blockRun = e.block ? s.blockRun + 1 : 0;
            return make(e);
        }
    }
}
