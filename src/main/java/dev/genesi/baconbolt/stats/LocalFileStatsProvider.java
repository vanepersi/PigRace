package dev.genesi.baconbolt.stats;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Local YAML fallback for development and desk use until an HTTP API is wired.
 */
public final class LocalFileStatsProvider implements StatsProvider {

    private final JavaPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    public LocalFileStatsProvider(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), fileName);
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public String id() {
        return "local";
    }

    @Override
    public synchronized void record(RaceResult result) {
        List<Map<String, Object>> history = new ArrayList<>();
        List<?> existing = data.getList("history");
        if (existing != null) {
            for (Object entry : existing) {
                if (entry instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cast = (Map<String, Object>) map;
                    history.add(cast);
                }
            }
        }

        Map<String, Object> row = new HashMap<>();
        row.put("arena", result.arena());
        row.put("path", result.path());
        row.put("started-at", result.startedAtEpochMs());
        row.put("ended-at", result.endedAtEpochMs());
        row.put("reason", result.reason());
        List<Map<String, Object>> placements = new ArrayList<>();
        for (RaceResult.Placement placement : result.placements()) {
            Map<String, Object> p = new HashMap<>();
            p.put("uuid", placement.uuid().toString());
            p.put("name", placement.name());
            p.put("place", placement.place());
            p.put("time-seconds", placement.timeSeconds());
            p.put("finished", placement.finished());
            placements.add(p);

            String base = "players." + placement.uuid();
            data.set(base + ".name", placement.name());
            data.set(base + ".races", data.getInt(base + ".races", 0) + 1);
            if (placement.finished() && placement.place() == 1) {
                data.set(base + ".wins", data.getInt(base + ".wins", 0) + 1);
            }
            if (placement.finished()) {
                double best = data.getDouble(base + ".best-time", Double.MAX_VALUE);
                if (placement.timeSeconds() < best) {
                    data.set(base + ".best-time", placement.timeSeconds());
                }
            }
        }
        row.put("placements", placements);
        history.add(row);
        // Keep last 200 races
        if (history.size() > 200) {
            history = new ArrayList<>(history.subList(history.size() - 200, history.size()));
        }
        data.set("history", history);
        save();
    }

    @Override
    public synchronized LeaderboardSnapshot leaderboard(String arena, int limit) {
        if (!data.isConfigurationSection("players")) {
            return LeaderboardSnapshot.empty();
        }
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (String key : data.getConfigurationSection("players").getKeys(false)) {
            String base = "players." + key;
            entries.add(new LeaderboardEntry(
                    data.getString(base + ".name", key),
                    key,
                    data.getInt(base + ".wins", 0),
                    data.getDouble(base + ".best-time", 0.0),
                    data.getInt(base + ".races", 0)
            ));
        }
        entries.sort(Comparator
                .comparingInt(LeaderboardEntry::wins).reversed()
                .thenComparingDouble(e -> e.bestTimeSeconds() <= 0 ? Double.MAX_VALUE : e.bestTimeSeconds()));
        if (entries.size() > limit) {
            entries = entries.subList(0, limit);
        }
        return new LeaderboardSnapshot(true, "local", List.copyOf(entries));
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save local pig race stats", ex);
        }
    }
}
