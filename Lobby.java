package id.zetminigames.core;

import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class Lobby {
    public final UUID host;
    public final GameSettings settings;
    public final Set<UUID> invited = new LinkedHashSet<>();
    public final Set<UUID> ready = new LinkedHashSet<>();
    public BukkitTask timeout;

    public Lobby(UUID host, GameSettings settings) {
        this.host = host;
        this.settings = settings;
    }
}
