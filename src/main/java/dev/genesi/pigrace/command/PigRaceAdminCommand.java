package dev.genesi.pigrace.command;

import dev.genesi.pigrace.PigRacePlugin;
import dev.genesi.pigrace.model.Arena;
import dev.genesi.pigrace.model.RacePath;
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

public final class PigRaceAdminCommand implements CommandExecutor, TabCompleter {

    private final PigRacePlugin plugin;

    public PigRaceAdminCommand(PigRacePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pigrace.admin")) {
            plugin.getMessageService().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /pigraceadmin create <arena>");
                    return true;
                }
                if (plugin.getArenaManager().get(args[1]).isPresent()) {
                    plugin.getMessageService().sendRaw(sender, "&cArena already exists.");
                    return true;
                }
                plugin.getArenaManager().create(args[1]);
                plugin.getMessageService().send(sender, "arena-created", Map.of("arena", args[1].toLowerCase(Locale.ROOT)));
            }
            case "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /pigraceadmin delete <arena>");
                    return true;
                }
                if (plugin.getArenaManager().delete(args[1])) {
                    plugin.getMessageService().send(sender, "arena-deleted", Map.of("arena", args[1].toLowerCase(Locale.ROOT)));
                } else {
                    plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
                }
            }
            case "setlobby" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                arena.setLobby(player.getLocation());
                plugin.getArenaManager().save();
                plugin.getMessageService().send(player, "lobby-set", Map.of("arena", arena.getName()));
            }
            case "setexit" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                arena.setExit(player.getLocation());
                plugin.getArenaManager().save();
                plugin.getMessageService().send(player, "exit-set", Map.of("arena", arena.getName()));
            }
            case "createpath" -> {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /pigraceadmin createpath <arena> <path>");
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                RacePath path = arena.getOrCreatePath(args[2]);
                plugin.getArenaManager().save();
                plugin.getMessageService().send(sender, "path-created", Map.of("path", path.getName(), "arena", arena.getName()));
            }
            case "deletepath" -> {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /pigraceadmin deletepath <arena> <path>");
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                if (arena.removePath(args[2])) {
                    plugin.getArenaManager().save();
                    plugin.getMessageService().send(sender, "path-deleted", Map.of("path", args[2].toLowerCase(Locale.ROOT), "arena", arena.getName()));
                } else {
                    plugin.getMessageService().sendRaw(sender, "&cPath not found.");
                }
            }
            case "addspawn" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("Usage: /pigraceadmin addspawn <arena> <path>");
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                RacePath path = arena.getOrCreatePath(args[2]);
                int index = path.addSpawn(player.getLocation());
                plugin.getArenaManager().save();
                plugin.getMessageService().send(player, "spawn-added", Map.of(
                        "index", String.valueOf(index),
                        "path", path.getName(),
                        "arena", arena.getName()
                ));
            }
            case "setfinish" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("Usage: /pigraceadmin setfinish <arena> <path> <a|b>");
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                RacePath path = arena.getOrCreatePath(args[2]);
                String corner = args[3].toLowerCase(Locale.ROOT);
                if (corner.equals("a")) {
                    path.setFinishA(player.getLocation());
                } else if (corner.equals("b")) {
                    path.setFinishB(player.getLocation());
                } else {
                    sender.sendMessage("Corner must be a or b");
                    return true;
                }
                plugin.getArenaManager().save();
                plugin.getMessageService().send(player, "finish-set", Map.of("corner", corner, "path", path.getName()));
            }
            case "addtrail" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("Usage: /pigraceadmin addtrail <arena> <path>");
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                RacePath path = arena.getOrCreatePath(args[2]);
                int index = path.addTrail(player.getLocation());
                plugin.getArenaManager().save();
                plugin.getMessageService().send(player, "trail-added", Map.of(
                        "index", String.valueOf(index),
                        "path", path.getName()
                ));
            }
            case "addpowerup" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("Usage: /pigraceadmin addpowerup <arena> <path>");
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                RacePath path = arena.getOrCreatePath(args[2]);
                int index = path.addPowerUp(player.getLocation());
                plugin.getArenaManager().save();
                plugin.getMessageService().send(player, "powerup-added", Map.of(
                        "index", String.valueOf(index),
                        "path", path.getName()
                ));
            }
            case "givejoinitem", "givecarrot" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                player.getInventory().addItem(plugin.getItemFactory().createJoinItem());
                plugin.getMessageService().send(player, "join-item-given");
            }
            case "setjoinarena" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /pigraceadmin setjoinarena <arena>");
                    return true;
                }
                plugin.getConfig().set("join-item.arena", args[1].toLowerCase(Locale.ROOT));
                plugin.saveConfig();
                plugin.getMessageService().send(sender, "join-item-bound", Map.of("arena", args[1].toLowerCase(Locale.ROOT)));
            }
            case "forcestop" -> {
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                plugin.getGameManager().forceStop(arena);
                plugin.getMessageService().send(sender, "force-stopped", Map.of("arena", arena.getName()));
            }
            case "reload" -> {
                plugin.reloadPlugin();
                plugin.getMessageService().send(sender, "reloaded");
            }
            case "list" -> {
                if (plugin.getArenaManager().all().isEmpty()) {
                    plugin.getMessageService().sendRaw(sender, "&7No arenas yet.");
                } else {
                    for (Arena arena : plugin.getArenaManager().all()) {
                        plugin.getMessageService().sendRaw(sender,
                                "&e" + arena.getName()
                                        + " &8| &7paths=" + arena.getPaths().size()
                                        + " &8| &7ready=" + arena.isReady());
                    }
                }
            }
            case "stats" -> {
                var snap = plugin.getStatsService().provider().leaderboard(
                        args.length >= 2 ? args[1] : "",
                        10
                );
                if (!snap.available()) {
                    plugin.getMessageService().sendRaw(sender, "&7" + snap.message());
                } else {
                    plugin.getMessageService().sendRaw(sender, "&dLeaderboard (&e" + plugin.getStatsService().provider().id() + "&d):");
                    int i = 1;
                    for (var entry : snap.entries()) {
                        plugin.getMessageService().sendRaw(sender,
                                "&e#" + i++ + " &f" + entry.name()
                                        + " &8| &awins &f" + entry.wins()
                                        + " &8| &bbest &f" + String.format("%.1f", entry.bestTimeSeconds()) + "s"
                                        + " &8| &7races &f" + entry.races());
                    }
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageService().sendRaw(sender, "&e/pigraceadmin create|delete <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/pigraceadmin setlobby|setexit <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/pigraceadmin createpath|deletepath <arena> <path>");
        plugin.getMessageService().sendRaw(sender, "&e/pigraceadmin addspawn <arena> <path>");
        plugin.getMessageService().sendRaw(sender, "&e/pigraceadmin addtrail <arena> <path> &7- lounge trail points");
        plugin.getMessageService().sendRaw(sender, "&e/pigraceadmin addpowerup <arena> <path> &7- Mario Kart boxes");
        plugin.getMessageService().sendRaw(sender, "&e/pigraceadmin setfinish <arena> <path> <a|b>");
        plugin.getMessageService().sendRaw(sender, "&e/pigraceadmin givecarrot | setjoinarena <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/pigraceadmin forcestop <arena> | list | stats | reload");
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.getMessageService().send(sender, "players-only");
        return null;
    }

    private Arena requireArena(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage("Usage requires an arena name.");
            return null;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(args[index]);
        if (arena.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[index]));
            return null;
        }
        return arena.get();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("pigrace.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of(
                    "create", "delete", "setlobby", "setexit", "createpath", "deletepath",
                    "addspawn", "addtrail", "addpowerup", "setfinish", "givejoinitem", "givecarrot",
                    "setjoinarena", "forcestop", "reload", "list", "stats"
            ), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("delete", "setlobby", "setexit", "createpath", "deletepath", "addspawn",
                    "addtrail", "addpowerup", "setfinish", "setjoinarena", "forcestop").contains(sub)) {
                return filter(plugin.getArenaManager().all().stream().map(Arena::getName).collect(Collectors.toList()), args[1]);
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("deletepath", "addspawn", "addtrail", "addpowerup", "setfinish", "createpath").contains(sub)) {
                return plugin.getArenaManager().get(args[1])
                        .map(a -> filter(a.getPaths().stream().map(RacePath::getName).collect(Collectors.toList()), args[2]))
                        .orElse(List.of());
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("setfinish")) {
            return filter(List.of("a", "b"), args[3]);
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
