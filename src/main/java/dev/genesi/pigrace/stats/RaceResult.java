package dev.genesi.pigrace.stats;

import java.util.List;
import java.util.UUID;

/**
 * Immutable race outcome passed to {@link StatsProvider} implementations.
 * Designed so a website API can ingest the same payload later.
 */
public record RaceResult(
        String arena,
        String path,
        long startedAtEpochMs,
        long endedAtEpochMs,
        String reason,
        List<Placement> placements
) {
    public record Placement(
            UUID uuid,
            String name,
            int place,
            double timeSeconds,
            boolean finished
    ) {
    }
}
