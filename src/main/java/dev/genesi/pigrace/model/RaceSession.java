package dev.genesi.pigrace.model;

import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RaceSession {

    public enum Phase {
        WAITING,
        COUNTDOWN,
        RACING,
        ENDED
    }

    private final String arenaName;
    private final Map<UUID, Racer> racers = new LinkedHashMap<>();
    private Phase phase = Phase.WAITING;
    private RacePath activePath;
    private long raceStartMillis;
    private int lobbySecondsLeft;
    private int startSecondsLeft;
    private int raceSecondsLeft;
    private BukkitTask tickTask;
    private final List<Placement> placements = new ArrayList<>();

    public RaceSession(String arenaName) {
        this.arenaName = arenaName.toLowerCase();
    }

    public String getArenaName() {
        return arenaName;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public RacePath getActivePath() {
        return activePath;
    }

    public void setActivePath(RacePath activePath) {
        this.activePath = activePath;
    }

    public Map<UUID, Racer> getRacers() {
        return racers;
    }

    public Racer addRacer(Player player) {
        Racer racer = new Racer(player.getUniqueId(), player.getName());
        racers.put(player.getUniqueId(), racer);
        return racer;
    }

    public Racer getRacer(UUID uuid) {
        return racers.get(uuid);
    }

    public boolean removeRacer(UUID uuid) {
        return racers.remove(uuid) != null;
    }

    public int size() {
        return racers.size();
    }

    public long getRaceStartMillis() {
        return raceStartMillis;
    }

    public void setRaceStartMillis(long raceStartMillis) {
        this.raceStartMillis = raceStartMillis;
    }

    public int getLobbySecondsLeft() {
        return lobbySecondsLeft;
    }

    public void setLobbySecondsLeft(int lobbySecondsLeft) {
        this.lobbySecondsLeft = lobbySecondsLeft;
    }

    public int getStartSecondsLeft() {
        return startSecondsLeft;
    }

    public void setStartSecondsLeft(int startSecondsLeft) {
        this.startSecondsLeft = startSecondsLeft;
    }

    public int getRaceSecondsLeft() {
        return raceSecondsLeft;
    }

    public void setRaceSecondsLeft(int raceSecondsLeft) {
        this.raceSecondsLeft = raceSecondsLeft;
    }

    public BukkitTask getTickTask() {
        return tickTask;
    }

    public void setTickTask(BukkitTask tickTask) {
        this.tickTask = tickTask;
    }

    public List<Placement> getPlacements() {
        return placements;
    }

    public void addPlacement(Placement placement) {
        placements.add(placement);
    }

    public boolean hasFinished(UUID uuid) {
        for (Placement placement : placements) {
            if (placement.uuid().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    public void cancelTask() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    public static final class Racer {
        private final UUID uuid;
        private final String name;
        private Pig pig;
        private Double previousScale;
        private boolean finished;

        public Racer(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public Pig getPig() {
            return pig;
        }

        public void setPig(Pig pig) {
            this.pig = pig;
        }

        public Double getPreviousScale() {
            return previousScale;
        }

        public void setPreviousScale(Double previousScale) {
            this.previousScale = previousScale;
        }

        public boolean isFinished() {
            return finished;
        }

        public void setFinished(boolean finished) {
            this.finished = finished;
        }
    }

    public record Placement(UUID uuid, String name, int place, double timeSeconds) {
    }
}
