package dev.genesi.baconbolt.stats;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Factory that builds the active {@link StatsProvider} from config.
 * Race code only talks to this service — never hard-codes storage.
 */
public final class StatsService {

    private final JavaPlugin plugin;
    private StatsProvider provider;

    public StatsService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String id = plugin.getConfig().getString("stats.provider", "none");
        if (id == null) {
            id = "none";
        }
        switch (id.toLowerCase()) {
            case "local" -> provider = new LocalFileStatsProvider(
                    plugin,
                    plugin.getConfig().getString("stats.local.file", "stats.yml")
            );
            case "http" -> {
                // Optional dual-write: HTTP primary, local mirror when local file is set
                StatsProvider http = new HttpApiStatsProvider(plugin);
                if (plugin.getConfig().getBoolean("stats.http.mirror-local", false)) {
                    StatsProvider local = new LocalFileStatsProvider(
                            plugin,
                            plugin.getConfig().getString("stats.local.file", "stats.yml")
                    );
                    provider = new CompositeStatsProvider(http, local);
                } else {
                    provider = http;
                }
            }
            default -> provider = new NoOpStatsProvider(plugin.getLogger());
        }
        plugin.getLogger().info("BaconBolt stats provider: " + provider.id());
    }

    public StatsProvider provider() {
        return provider;
    }

    public void record(RaceResult result) {
        if (provider != null) {
            provider.record(result);
        }
    }

    /** Fan-out helper used when mirroring HTTP + local. */
    static final class CompositeStatsProvider implements StatsProvider {
        private final StatsProvider primary;
        private final StatsProvider secondary;

        CompositeStatsProvider(StatsProvider primary, StatsProvider secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        @Override
        public String id() {
            return primary.id() + "+" + secondary.id();
        }

        @Override
        public void record(RaceResult result) {
            primary.record(result);
            secondary.record(result);
        }

        @Override
        public LeaderboardSnapshot leaderboard(String arena, int limit) {
            LeaderboardSnapshot snap = secondary.leaderboard(arena, limit);
            if (snap.available()) {
                return snap;
            }
            return primary.leaderboard(arena, limit);
        }
    }
}
