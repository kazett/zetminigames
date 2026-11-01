package id.zetminigames.core;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Simpan kondisi pemain sebelum game, dibalikin setelah game. */
public final class PlayerSnapshot {
    private final Location location;
    private final GameMode mode;
    private final ItemStack[] contents;
    private final double health;
    private final int food;
    private final int level;
    private final float exp;

    public PlayerSnapshot(Player p) {
        this.location = p.getLocation();
        this.mode = p.getGameMode();
        this.contents = p.getInventory().getContents().clone();
        this.health = p.getHealth();
        this.food = p.getFoodLevel();
        this.level = p.getLevel();
        this.exp = p.getExp();
    }

    public void restore(Player p) {
        p.getInventory().clear();
        p.getInventory().setContents(contents);
        p.setGameMode(mode);
        p.setHealth(Math.max(1.0, Math.min(health, 20.0)));
        p.setFoodLevel(food);
        p.setLevel(level);
        p.setExp(exp);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.teleport(location);
    }
}
