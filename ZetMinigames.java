package id.zetminigames;

import id.zetminigames.core.Commands;
import id.zetminigames.core.GameListener;
import id.zetminigames.core.GameManager;
import id.zetminigames.hud.PackListener;
import id.zetminigames.panel.PofGui;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ZetMinigames extends JavaPlugin {
    private GameManager manager;
    private final Set<UUID> packReady = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        manager = new GameManager(this);
        cleanupStaleWorlds();
        Bukkit.getPluginManager().registerEvents(new GameListener(manager), this);
        Bukkit.getPluginManager().registerEvents(new PackListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PofGui.Events(this), this);

        Commands commands = new Commands(this);
        for (String name : new String[]{"panel", "pof"}) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(commands);
                c.setTabCompleter(commands);
            }
        }
        getLogger().info("ZetMinigames aktif.");
    }

    private void migrateConfig() {
        int rev = getConfig().getInt("battleroyale.rev", 0);
        if (rev >= 3) return;
        if (rev < 2) {
            getConfig().set("battleroyale.sky.y", 300);
            getConfig().set("battleroyale.sky.radius", 24);
            getConfig().set("battleroyale.pvp-delay-seconds", 30);
        }
        // rev 3: zona 1000x1000, jumlah tahap acak 8-12, titik tengah otomatis di daratan
        getConfig().set("battleroyale.rev", 3);
        getConfig().set("battleroyale.zone.start-size", 1000);
        getConfig().set("battleroyale.zone.stages", null);
        getConfig().set("battleroyale.zone.min-stages", 8);
        getConfig().set("battleroyale.zone.max-stages", 12);
        getConfig().set("battleroyale.center.auto", true);
        saveConfig();
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    public GameManager manager() {
        return manager;
    }

    // ===== Resource pack / HUD =====

    public boolean hudEnabled() {
        return !getConfig().getString("resource-pack.url", "").isBlank();
    }

    public boolean packReady(Player p) {
        return hudEnabled() && packReady.contains(p.getUniqueId());
    }

    public void setPackReady(UUID id, boolean ok) {
        if (ok) packReady.add(id);
        else packReady.remove(id);
    }

    public int headAdvance() {
        return getConfig().getInt("hud.head-advance", 8);
    }

    public int slotWidth() {
        return getConfig().getInt("hud.slot-width", 10);
    }

    public void sendPack(Player p) {
        String url = getConfig().getString("resource-pack.url", "");
        if (url.isBlank()) return;
        byte[] hash = hexToBytes(getConfig().getString("resource-pack.sha1", ""));
        boolean required = getConfig().getBoolean("resource-pack.required", true);
        String prompt = getConfig().getString("resource-pack.prompt", "");
        p.setResourcePack(url, hash, prompt.isBlank() ? null : Component.text(prompt), required);
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null) return null;
        hex = hex.trim();
        if (hex.length() != 40) return null;
        try {
            byte[] out = new byte[20];
            for (int i = 0; i < 20; i++) {
                out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Hapus sisa world pof_* kalau server sempat crash. */
    private void cleanupStaleWorlds() {
        File[] dirs = Bukkit.getWorldContainer().listFiles();
        if (dirs == null) return;
        for (File d : dirs) {
            if (d.isDirectory() && d.getName().startsWith("pof_") && Bukkit.getWorld(d.getName()) == null) {
                GameManager.deleteRecursive(d);
            }
        }
    }
}
