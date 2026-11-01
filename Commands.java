package id.zetminigames.core;

import id.zetminigames.panel.Panels;
import id.zetminigames.ZetMinigames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class Commands implements CommandExecutor, TabCompleter {
    private final ZetMinigames plugin;

    public Commands(ZetMinigames plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Cuma buat player.");
            return true;
        }
        GameManager m = plugin.manager();
        if (cmd.getName().equalsIgnoreCase("panel")) {
            if (m.gameOf(p.getUniqueId()) != null) {
                Panels.openInGame(plugin, p);
            } else if (m.lobbyOf(p.getUniqueId()) != null) {
                Panels.openLobby(plugin, p);
            } else {
                Panels.openMain(plugin, p);
            }
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "ready" -> m.ready(p);
            case "leave" -> m.leave(p);
            case "cancel" -> m.cancel(p);
            default -> p.sendMessage(Component.text("Pakai /panel", NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("pof") && args.length == 1) {
            return List.of("ready", "leave", "cancel");
        }
        return List.of();
    }
}
