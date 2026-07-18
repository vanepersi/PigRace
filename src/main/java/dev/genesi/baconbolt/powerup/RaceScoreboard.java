package dev.genesi.baconbolt.powerup;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player sidebar used for the Mario Kart item-box roulette + race HUD.
 */
public final class RaceScoreboard {

    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public void showSpin(Player player, String titleLegacy, String effectLegacy, boolean locked) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective objective = board.getObjective("baconbolt");
        if (objective == null) {
            objective = board.registerNewObjective(
                    "baconbolt",
                    Criteria.DUMMY,
                    LegacyComponentSerializer.legacyAmpersand().deserialize(titleLegacy)
            );
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(titleLegacy));
        }

        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        String line1 = color(locked ? "&a&l>>> " + strip(effectLegacy) + " &a&l<<<" : "&e" + strip(effectLegacy));
        String line2 = color(locked ? "&7Effect locked in!" : "&8spinning...");
        String line3 = color("&8────────────");
        objective.getScore(unique(line1, 1)).setScore(3);
        objective.getScore(unique(line2, 2)).setScore(2);
        objective.getScore(unique(line3, 3)).setScore(1);

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    public void showRaceHud(Player player, String path, int timeLeft) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective objective = board.getObjective("baconbolt");
        if (objective == null) {
            objective = board.registerNewObjective(
                    "baconbolt",
                    Criteria.DUMMY,
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&d&lBACON BOLT")
            );
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&d&lBACON BOLT"));
        }
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }
        objective.getScore(unique(color("&7Path &b" + path), 1)).setScore(3);
        objective.getScore(unique(color("&7Time &e" + timeLeft + "s"), 2)).setScore(2);
        objective.getScore(unique(color("&7Hit &6gold boxes&7!"), 3)).setScore(1);
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    public void clear(Player player) {
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void clear(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            clear(player);
        } else {
            boards.remove(uuid);
        }
    }

    private static String color(String legacy) {
        return LegacyComponentSerializer.legacySection().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(legacy)
        );
    }

    private static String strip(String legacy) {
        if (legacy == null) {
            return "";
        }
        return legacy.replaceAll("&[0-9a-fk-or]", "").replaceAll("§[0-9a-fk-or]", "");
    }

    private static String unique(String text, int slot) {
        return text + "§" + Integer.toHexString(slot);
    }
}
