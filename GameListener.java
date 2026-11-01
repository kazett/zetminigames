package id.zetminigames.core;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class GameListener implements Listener {
    private final GameManager manager;

    public GameListener(GameManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Match g = manager.gameOf(p.getUniqueId());
        if (g != null) g.onDamage(p, e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Match g = manager.gameOf(e.getPlayer().getUniqueId());
        if (g != null) g.onSwing(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        Match g = manager.gameOf(p.getUniqueId());
        if (g != null) g.onAttack(p, e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p)) return;
        Match g = manager.gameOf(p.getUniqueId());
        if (g != null) g.onLaunch(p, e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (manager.gameOf(e.getPlayer().getUniqueId()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedPosition()) return;
        Match g = manager.gameOf(e.getPlayer().getUniqueId());
        if (g != null) g.onMove(e.getPlayer(), e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Match g = manager.gameOf(e.getPlayer().getUniqueId());
        if (g != null) g.onBlockPlace(e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Match g = manager.gameOf(e.getPlayer().getUniqueId());
        if (g != null) g.onBlockBreak(e);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Match g = manager.gameOf(e.getEntity().getUniqueId());
        if (g != null) g.onDeath(e.getEntity(), e);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Match g = manager.gameOf(e.getPlayer().getUniqueId());
        if (g != null) g.onRespawn(e.getPlayer(), e);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Match g = manager.gameOf(e.getPlayer().getUniqueId());
        if (g != null) g.onQuit(e.getPlayer());
        else manager.forget(e.getPlayer().getUniqueId());
    }
}
