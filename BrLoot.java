package id.zetminigames.battleroyale;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class BrLoot {
    private BrLoot() {}

    private static final int MIN_ITEMS = 3;
    private static final int MAX_ITEMS = 10;

    private record Entry(Material material, int min, int max, int weight) {}

    private static final List<Entry> POOL = new ArrayList<>();
    private static int totalWeight;

    private static void add(Material material, int min, int max, int weight) {
        POOL.add(new Entry(material, min, max, weight));
        totalWeight += weight;
    }

    static {
        add(Material.WOODEN_SWORD, 1, 1, 6);
        add(Material.STONE_SWORD, 1, 1, 8);
        add(Material.IRON_SWORD, 1, 1, 5);
        add(Material.DIAMOND_SWORD, 1, 1, 2);
        add(Material.STONE_AXE, 1, 1, 6);
        add(Material.IRON_AXE, 1, 1, 4);
        add(Material.DIAMOND_AXE, 1, 1, 1);
        add(Material.WOODEN_PICKAXE, 1, 1, 6);
        add(Material.STONE_PICKAXE, 1, 1, 8);
        add(Material.IRON_PICKAXE, 1, 1, 5);
        add(Material.DIAMOND_PICKAXE, 1, 1, 1);
        add(Material.LEATHER_HELMET, 1, 1, 5);
        add(Material.LEATHER_CHESTPLATE, 1, 1, 5);
        add(Material.LEATHER_LEGGINGS, 1, 1, 5);
        add(Material.LEATHER_BOOTS, 1, 1, 5);
        add(Material.CHAINMAIL_HELMET, 1, 1, 3);
        add(Material.CHAINMAIL_CHESTPLATE, 1, 1, 3);
        add(Material.CHAINMAIL_LEGGINGS, 1, 1, 3);
        add(Material.CHAINMAIL_BOOTS, 1, 1, 3);
        add(Material.IRON_HELMET, 1, 1, 3);
        add(Material.IRON_CHESTPLATE, 1, 1, 3);
        add(Material.IRON_LEGGINGS, 1, 1, 3);
        add(Material.IRON_BOOTS, 1, 1, 3);
        add(Material.DIAMOND_HELMET, 1, 1, 1);
        add(Material.DIAMOND_CHESTPLATE, 1, 1, 1);
        add(Material.DIAMOND_LEGGINGS, 1, 1, 1);
        add(Material.DIAMOND_BOOTS, 1, 1, 1);
        add(Material.NETHERITE_SWORD, 1, 1, 1);
        add(Material.NETHERITE_HELMET, 1, 1, 1);
        add(Material.NETHERITE_CHESTPLATE, 1, 1, 1);
        add(Material.NETHERITE_LEGGINGS, 1, 1, 1);
        add(Material.NETHERITE_BOOTS, 1, 1, 1);
        add(Material.SHIELD, 1, 1, 3);
        add(Material.BOW, 1, 1, 5);
        add(Material.CROSSBOW, 1, 1, 4);
        add(Material.ARROW, 4, 12, 8);
        add(Material.ENDER_PEARL, 1, 3, 8);
        add(Material.GOLDEN_APPLE, 1, 3, 8);
        add(Material.COOKED_BEEF, 3, 8, 10);
        add(Material.COOKED_PORKCHOP, 3, 8, 8);
        add(Material.COOKED_CHICKEN, 3, 8, 6);
        add(Material.BREAD, 4, 10, 8);
        add(Material.COBWEB, 2, 6, 7);
        add(Material.WATER_BUCKET, 1, 1, 7);
        add(Material.LAVA_BUCKET, 1, 1, 5);
        add(Material.FISHING_ROD, 1, 1, 3);
        add(Material.WIND_CHARGE, 2, 6, 5);
        add(Material.MACE, 1, 1, 4);
        addNamed("WOODEN_SPEAR", 1, 1, 6);
        addNamed("STONE_SPEAR", 1, 1, 8);
        addNamed("COPPER_SPEAR", 1, 1, 6);
        addNamed("IRON_SPEAR", 1, 1, 5);
        addNamed("DIAMOND_SPEAR", 1, 1, 1);
        add(Material.COBBLESTONE, 12, 32, 12);
        add(Material.OAK_PLANKS, 12, 32, 12);
    }

    private static void addNamed(String name, int min, int max, int weight) {
        Material material = Material.matchMaterial(name);
        if (material != null) add(material, min, max, weight);
    }

    private static Entry pick(ThreadLocalRandom random) {
        int roll = random.nextInt(totalWeight);
        for (Entry entry : POOL) {
            roll -= entry.weight();
            if (roll < 0) return entry;
        }
        return POOL.get(0);
    }

    public static void fill(Inventory inventory) {
        inventory.clear();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) slots.add(i);
        Collections.shuffle(slots);
        int count = MIN_ITEMS + random.nextInt(MAX_ITEMS - MIN_ITEMS + 1);
        for (int i = 0; i < count && i < slots.size(); i++) {
            Entry entry = pick(random);
            int amount = entry.min() >= entry.max() ? entry.min()
                    : entry.min() + random.nextInt(entry.max() - entry.min() + 1);
            inventory.setItem(slots.get(i), new ItemStack(entry.material(), amount));
        }
    }
}
