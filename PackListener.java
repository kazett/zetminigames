package id.zetminigames.hud;

import id.zetminigames.ZetMinigames;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** Kirim resource pack pas join, dan catat siapa yang udah berhasil load. */
public class PackListener implements Listener {
    private final ZetMinigames plugin;

    public PackListener(ZetMinigames plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.hudEnabled()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) plugin.sendPack(e.getPlayer());
        }, 20L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        PlayerResourcePackStatusEvent.Status st = e.getStatus();
        if (st == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            plugin.setPackReady(e.getPlayer().getUniqueId(), true);
        } else if (st == PlayerResourcePackStatusEvent.Status.DECLINED
                || st == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || st == PlayerResourcePackStatusEvent.Status.FAILED_RELOAD
                || st == PlayerResourcePackStatusEvent.Status.INVALID_URL) {
            plugin.setPackReady(e.getPlayer().getUniqueId(), false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.setPackReady(e.getPlayer().getUniqueId(), false);
    }
}
