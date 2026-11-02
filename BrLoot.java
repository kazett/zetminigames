package id.zetminigames.battleroyale;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Loot chest Battle Royale — fokus PVP, cukup generous.
 * Wind charge / ender pearl / gapple / gear sering keluar.
 */
public final class BrLoot {
    private BrLoot() {}

    private static final int MIN_ITEMS = 5;
    private static final int MAX_ITEMS = 12;

    private record Entry(Material material, int min, int max, int weight, PotionType potion) {}

    private static final double ENCHANT_CHANCE = 0.40;

    private record Opt(String key, int max, int weight) {}

    private static final List<Opt> SWORD = List.of(new Opt("sharpness", 4, 6), new Opt("knockback", 2, 2), new Opt("fire_aspect", 2, 2));
    private static final List<Opt> AXE = List.of(new Opt("sharpness", 3, 4), new Opt("efficiency", 3, 2));
    private static final List<Opt> PICKAXE = List.of(new Opt("efficiency", 4, 3), new Opt("unbreaking", 2, 2));
    private static final List<Opt> SPEAR = List.of(new Opt("sharpness", 3, 3), new Opt("lunge", 2, 2));
    private static final List<Opt> ARMOR = List.of(new Opt("protection", 4, 6), new Opt("projectile_protection", 3, 2), new Opt("unbreaking", 2, 2));
    private static final List<Opt> BOOTS = List.of(new Opt("protection", 4, 5), new Opt("feather_falling", 4, 3), new Opt("unbreaking", 2, 1));
    private static final List<Opt> BOW = List.of(new Opt("power", 4, 5), new Opt("punch", 2, 2), new Opt("flame", 1, 1));
    private static final List<Opt> CROSSBOW = List.of(new Opt("quick_charge", 3, 3), new Opt("piercing", 3, 2), new Opt("multishot", 1, 1));
    private static final List<Opt> MACE = List.of(new Opt("density", 3, 3), new Opt("wind_burst", 2, 2));
    private static final List<Opt> SHIELD = List.of(new Opt("unbreaking", 3, 2));

    private static final List<Entry> POOL = new ArrayList<>();
    private static int totalWeight;

    private static void add(Material material, int min, int max, int weight) {
        POOL.add(new Entry(material, min, max, weight, null));
        totalWeight += weight;
    }

    private static void addPotion(PotionType type, int weight) {
        POOL.add(new Entry(Material.POTION, 1, 1, weight, type));
        totalWeight += weight;
    }

    private static void addSplash(PotionType type, int weight) {
        POOL.add(new Entry(Material.SPLASH_POTION, 1, 1, weight, type));
        totalWeight += weight;
    }

    static {
        // ---- weapons (tinggi) ----
        add(Material.STONE_SWORD, 1, 1, 10);
        add(Material.IRON_SWORD, 1, 1, 12);
        add(Material.DIAMOND_SWORD, 1, 1, 6);
        add(Material.NETHERITE_SWORD, 1, 1, 2);
        add(Material.STONE_AXE, 1, 1, 8);
        add(Material.IRON_AXE, 1, 1, 8);
        add(Material.DIAMOND_AXE, 1, 1, 3);
        add(Material.BOW, 1, 1, 10);
        add(Material.CROSSBOW, 1, 1, 8);
        add(Material.ARROW, 8, 24, 14);
        add(Material.SPECTRAL_ARROW, 4, 12, 5);
        add(Material.MACE, 1, 1, 5);
        add(Material.TRIDENT, 1, 1, 3);
        add(Material.SHIELD, 1, 1, 8);
        addNamed("WOODEN_SPEAR", 1, 1, 5);
        addNamed("STONE_SPEAR", 1, 1, 7);
        addNamed("IRON_SPEAR", 1, 1, 6);
        addNamed("DIAMOND_SPEAR", 1, 1, 2);

        // ---- armor ----
        add(Material.CHAINMAIL_HELMET, 1, 1, 5);
        add(Material.CHAINMAIL_CHESTPLATE, 1, 1, 5);
        add(Material.CHAINMAIL_LEGGINGS, 1, 1, 5);
        add(Material.CHAINMAIL_BOOTS, 1, 1, 5);
        add(Material.IRON_HELMET, 1, 1, 8);
        add(Material.IRON_CHESTPLATE, 1, 1, 8);
        add(Material.IRON_LEGGINGS, 1, 1, 8);
        add(Material.IRON_BOOTS, 1, 1, 8);
        add(Material.DIAMOND_HELMET, 1, 1, 4);
        add(Material.DIAMOND_CHESTPLATE, 1, 1, 4);
        add(Material.DIAMOND_LEGGINGS, 1, 1, 4);
        add(Material.DIAMOND_BOOTS, 1, 1, 4);
        add(Material.NETHERITE_HELMET, 1, 1, 1);
        add(Material.NETHERITE_CHESTPLATE, 1, 1, 1);
        add(Material.NETHERITE_LEGGINGS, 1, 1, 1);
        add(Material.NETHERITE_BOOTS, 1, 1, 1);

        // ---- combat utility (tinggi — jangan pelit) ----
        add(Material.ENDER_PEARL, 2, 6, 16);
        add(Material.WIND_CHARGE, 4, 12, 16);
        add(Material.GOLDEN_APPLE, 2, 5, 14);
        add(Material.ENCHANTED_GOLDEN_APPLE, 1, 1, 2);
        add(Material.TOTEM_OF_UNDYING, 1, 1, 4);
        add(Material.COBWEB, 4, 12, 10);
        add(Material.TNT, 2, 6, 6);
        add(Material.FLINT_AND_STEEL, 1, 1, 5);
        add(Material.FIRE_CHARGE, 4, 10, 5);
        add(Material.SNOWBALL, 8, 16, 6);
        add(Material.EGG, 8, 16, 4);

        // ---- tools (sedang) ----
        add(Material.IRON_PICKAXE, 1, 1, 6);
        add(Material.DIAMOND_PICKAXE, 1, 1, 3);
        add(Material.IRON_SHOVEL, 1, 1, 3);
        add(Material.WATER_BUCKET, 1, 1, 8);
        add(Material.LAVA_BUCKET, 1, 1, 5);
        add(Material.FISHING_ROD, 1, 1, 3);

        // ---- potions ----
        addPotion(PotionType.SWIFTNESS, 7);
        addPotion(PotionType.STRENGTH, 6);
        addPotion(PotionType.HEALING, 7);
        addPotion(PotionType.FIRE_RESISTANCE, 5);
        addPotion(PotionType.INVISIBILITY, 4);
        addPotion(PotionType.NIGHT_VISION, 3);
        addPotion(PotionType.TURTLE_MASTER, 2);
        addSplash(PotionType.HARMING, 5);
        addSplash(PotionType.POISON, 4);
        addSplash(PotionType.SLOWNESS, 4);
        addSplash(PotionType.WEAKNESS, 3);

        // ---- food ----
        add(Material.COOKED_BEEF, 4, 12, 8);
        add(Material.COOKED_PORKCHOP, 4, 12, 6);
        add(Material.GOLDEN_CARROT, 4, 10, 6);
        add(Material.BREAD, 6, 16, 5);

        // ---- blocks (sedikit aja, biar gak sampah) ----
        add(Material.COBBLESTONE, 16, 32, 4);
        add(Material.OAK_PLANKS, 16, 32, 4);
        add(Material.OBSIDIAN, 4, 12, 3);
        add(Material.ENDER_CHEST, 1, 1, 2);
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
            ItemStack stack = new ItemStack(entry.material(), amount);
            if (entry.potion() != null && stack.getItemMeta() instanceof PotionMeta pm) {
                pm.setBasePotionType(entry.potion());
                stack.setItemMeta(pm);
            }
            enchant(stack, random);
            inventory.setItem(slots.get(i), stack);
        }
    }

    private static List<Opt> optionsFor(String n) {
        if (n.endsWith("_SWORD")) return SWORD;
        if (n.endsWith("_AXE")) return AXE;
        if (n.endsWith("_PICKAXE")) return PICKAXE;
        if (n.endsWith("_SPEAR")) return SPEAR;
        if (n.endsWith("_BOOTS")) return BOOTS;
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")) return ARMOR;
        if (n.equals("BOW")) return BOW;
        if (n.equals("CROSSBOW")) return CROSSBOW;
        if (n.equals("MACE")) return MACE;
        if (n.equals("SHIELD")) return SHIELD;
        if (n.equals("TRIDENT")) return SWORD;
        return null;
    }

    private static int cap(int max, String n) {
        if (n.startsWith("NETHERITE")) return Math.min(max, 2);
        if (n.startsWith("DIAMOND")) return Math.min(max, 3);
        return max;
    }

    private static int rollLevel(int max, ThreadLocalRandom random) {
        int total = max * (max + 1) / 2;
        int r = random.nextInt(total);
        for (int level = 1; level <= max; level++) {
            r -= (max - level + 1);
            if (r < 0) return level;
        }
        return 1;
    }

    private static void enchant(ItemStack stack, ThreadLocalRandom random) {
        String n = stack.getType().name();
        List<Opt> options = optionsFor(n);
        if (options == null || random.nextDouble() >= ENCHANT_CHANCE) return;
        int total = 0;
        for (Opt o : options) total += o.weight();
        int r = random.nextInt(total);
        Opt chosen = options.get(0);
        for (Opt o : options) {
            r -= o.weight();
            if (r < 0) { chosen = o; break; }
        }
        Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(chosen.key()));
        if (ench == null) return;
        stack.addUnsafeEnchantment(ench, rollLevel(cap(chosen.max(), n), random));
    }
}
