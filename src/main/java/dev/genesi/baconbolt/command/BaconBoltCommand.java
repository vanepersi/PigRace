package dev.genesi.baconbolt.command;

import dev.genesi.baconbolt.BaconBoltPlugin;
import dev.genesi.baconbolt.model.Arena;
import dev.genesi.baconbolt.model.RaceSession;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class BaconBoltCommand implements CommandExecutor, TabCompleter {

    private final BaconBoltPlugin plugin;

    public BaconBoltCommand(BaconBoltPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("baconbolt.use")) {
            plugin.getMessageService().send(player, "no-permission");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> {
                String arenaName = args.length >= 2
                        ? args[1]
                        : plugin.getConfig().getString("join-item.arena", "lounge");
                Optional<Arena> arena = plugin.getArenaManager().get(arenaName);
                if (arena.isEmpty()) {
                    plugin.getMessageService().send(player, "arena-not-found", Map.of("arena", arenaName));
                    return true;
                }
                plugin.getGameManager().tryJoin(player, arena.get());
            }
            case "leave" -> plugin.getGameManager().leave(player, true);
            case "info" -> {
                Optional<RaceSession> session = plugin.getGameManager().getByPlayer(player.getUniqueId());
                if (session.isEmpty()) {
                    plugin.getMessageService().send(player, "not-playing");
                } else {
                    RaceSession s = session.get();
                    plugin.getMessageService().sendRaw(player,
                            "&7Arena: &e" + s.getArenaName()
                                    + " &8| &7Phase: &e" + s.getPhase()
                                    + " &8| &7Players: &e" + s.size()
                                    + (s.getActivePath() == null ? "" : " &8| &7Path: &b" + s.getActivePath().getName()));
                }
            }
            case "help" -> sendHelp(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        plugin.getMessageService().sendRaw(player, "&e/baconbolt join [arena] &7- join a lounge race");
        plugin.getMessageService().sendRaw(player, "&e/baconbolt leave &7- leave the race");
        plugin.getMessageService().sendRaw(player, "&e/baconbolt info &7- current race status");
        plugin.getMessageService().sendRaw(player, "&7Or &eeat&7 a Bacon Bolt carrot to join!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("join", "leave", "info", "help"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            return filter(plugin.getArenaManager().all().stream().map(Arena::getName).collect(Collectors.toList()), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(option);
            }
        }
        return out;
    }
}
