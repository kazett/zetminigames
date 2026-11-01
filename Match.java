package id.zetminigames.core;

import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.List;
import java.util.UUID;

public interface Match {
    boolean begin();
    List<UUID> playerIds();
    boolean isTestMode();
    int rounds();
    void testFinish(Player p);
    void testDraw(Player p);
    /** Tombol "Seri (tes)" muncul di /panel mode tes? Game tanpa konsep seri (Battle Royale) override jadi false. */
    default boolean hasTestDraw() { return true; }
    void forfeit(Player p);
    void forceShutdown();
    void onDamage(Player victim, EntityDamageEvent e);
    void onMove(Player p, PlayerMoveEvent e);
    void onBlockPlace(BlockPlaceEvent e);
    void onBlockBreak(BlockBreakEvent e);
    void onDeath(Player p, PlayerDeathEvent e);
    void onRespawn(Player p, PlayerRespawnEvent e);
    void onQuit(Player p);
    default void onAttack(Player attacker, EntityDamageByEntityEvent e) {}
    default void onSwing(Player p) {}
    default void onLaunch(Player shooter, ProjectileLaunchEvent e) {}
}
