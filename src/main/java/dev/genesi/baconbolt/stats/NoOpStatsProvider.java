package dev.genesi.baconbolt.stats;

import java.util.logging.Logger;

/** Default no-op provider — race still works; nothing is persisted. */
public final class NoOpStatsProvider implements StatsProvider {

    private final Logger logger;

    public NoOpStatsProvider(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String id() {
        return "none";
    }

    @Override
    public void record(RaceResult result) {
        logger.fine("Stats provider 'none' — skipping race result for arena " + result.arena());
    }
}
