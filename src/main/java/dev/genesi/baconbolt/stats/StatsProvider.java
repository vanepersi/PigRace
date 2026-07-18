package dev.genesi.baconbolt.stats;

/**
 * Pluggable stats / leaderboard sink.
 * Swap providers in config without changing race logic — wire your website API here later.
 */
public interface StatsProvider {

    String id();

    /**
     * Record a finished race. Implementations must not throw; log and fail soft.
     */
    void record(RaceResult result);

    /**
     * Optional hook for a future website to pull leaderboard rows.
     * Default is empty — HTTP provider can implement when your API is ready.
     */
    default LeaderboardSnapshot leaderboard(String arena, int limit) {
        return LeaderboardSnapshot.empty();
    }

    record LeaderboardEntry(String name, String uuid, int wins, double bestTimeSeconds, int races) {
    }

    record LeaderboardSnapshot(boolean available, String message, java.util.List<LeaderboardEntry> entries) {
        public static LeaderboardSnapshot empty() {
            return new LeaderboardSnapshot(false, "Leaderboard not configured yet.", java.util.List.of());
        }
    }
}
