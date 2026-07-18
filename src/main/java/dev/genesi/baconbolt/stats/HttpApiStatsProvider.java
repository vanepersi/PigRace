package dev.genesi.baconbolt.stats;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.StringJoiner;
import java.util.logging.Level;

/**
 * HTTP stats sink for a future website / club API.
 * Enabled via config; fails soft when the API is unreachable.
 * Payload is JSON so you can point {@code stats.http.url} at your own endpoint later.
 */
public final class HttpApiStatsProvider implements StatsProvider {

    private final JavaPlugin plugin;
    private final String url;
    private final String method;
    private final String authHeader;
    private final ConfigurationSection headers;
    private final int timeoutSeconds;
    private final boolean includePlacements;
    private final HttpClient client;

    public HttpApiStatsProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.url = plugin.getConfig().getString("stats.http.url", "");
        this.method = plugin.getConfig().getString("stats.http.method", "POST");
        this.authHeader = plugin.getConfig().getString("stats.http.auth-header", "");
        this.headers = plugin.getConfig().getConfigurationSection("stats.http.headers");
        this.timeoutSeconds = Math.max(1, plugin.getConfig().getInt("stats.http.timeout-seconds", 5));
        this.includePlacements = plugin.getConfig().getBoolean("stats.http.include-placements", true);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Override
    public String id() {
        return "http";
    }

    @Override
    public void record(RaceResult result) {
        if (url == null || url.isBlank() || url.contains("example.com")) {
            plugin.getLogger().fine("HTTP stats URL not configured — skipping.");
            return;
        }
        String body = toJson(result);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .header("Content-Type", "application/json");
                if (authHeader != null && !authHeader.isBlank()) {
                    builder.header("Authorization", authHeader);
                }
                if (headers != null) {
                    for (String key : headers.getKeys(false)) {
                        builder.header(key, String.valueOf(headers.get(key)));
                    }
                }
                String verb = method == null ? "POST" : method.toUpperCase();
                if ("PUT".equals(verb)) {
                    builder.PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                } else {
                    builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                }
                HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    plugin.getLogger().warning("BaconBolt stats API returned HTTP " + response.statusCode());
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "BaconBolt stats API request failed (race still counted locally if enabled)", ex);
            }
        });
    }

    @Override
    public LeaderboardSnapshot leaderboard(String arena, int limit) {
        return new LeaderboardSnapshot(
                false,
                "HTTP leaderboard GET is reserved for your website API — set stats.provider and implement retrieval when ready.",
                java.util.List.of()
        );
    }

    private String toJson(RaceResult result) {
        StringJoiner placements = new StringJoiner(",", "[", "]");
        if (includePlacements) {
            for (RaceResult.Placement p : result.placements()) {
                placements.add("{"
                        + "\"uuid\":\"" + escape(p.uuid().toString()) + "\","
                        + "\"name\":\"" + escape(p.name()) + "\","
                        + "\"place\":" + p.place() + ","
                        + "\"timeSeconds\":" + p.timeSeconds() + ","
                        + "\"finished\":" + p.finished()
                        + "}");
            }
        }
        return "{"
                + "\"game\":\"baconbolt\","
                + "\"arena\":\"" + escape(result.arena()) + "\","
                + "\"path\":\"" + escape(result.path()) + "\","
                + "\"startedAt\":" + result.startedAtEpochMs() + ","
                + "\"endedAt\":" + result.endedAtEpochMs() + ","
                + "\"reason\":\"" + escape(result.reason()) + "\","
                + "\"placements\":" + placements
                + "}";
    }

    private static String escape(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
