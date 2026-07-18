package dev.genesi.baconbolt.model;

import org.bukkit.Location;
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
    private BukkitTask fastTask;
    private final List<Placement> placements = new ArrayList<>();
    private final List<dev.genesi.baconbolt.powerup.PowerUpBox> activeBoxes = new ArrayList<>();
    private final Map<UUID, Boolean> spinning = new LinkedHashMap<>();

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
        spinning.remove(uuid);
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

    public BukkitTask getFastTask() {
        return fastTask;
    }

    public void setFastTask(BukkitTask fastTask) {
        this.fastTask = fastTask;
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

    public List<dev.genesi.baconbolt.powerup.PowerUpBox> getActiveBoxes() {
        return activeBoxes;
    }

    public boolean isSpinning(UUID uuid) {
        return Boolean.TRUE.equals(spinning.get(uuid));
    }

    public void setSpinning(UUID uuid, boolean value) {
        if (value) {
            spinning.put(uuid, true);
        } else {
            spinning.remove(uuid);
        }
    }

    public void cancelTask() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (fastTask != null) {
            fastTask.cancel();
            fastTask = null;
        }
    }

    public static final class Racer {
        private final UUID uuid;
        private final String name;
        private Pig pig;
        private Double previousScale;
        private boolean finished;
        private boolean animating;
        /** Locked spawn pad during countdown — pig is held here until GO. */
        private Location startSpawn;

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

        public boolean isAnimating() {
            return animating;
        }

        public void setAnimating(boolean animating) {
            this.animating = animating;
        }

        public Location getStartSpawn() {
            return startSpawn == null ? null : startSpawn.clone();
        }

        public void setStartSpawn(Location startSpawn) {
            this.startSpawn = startSpawn == null ? null : startSpawn.clone();
        }
    }

    public record Placement(UUID uuid, String name, int place, double timeSeconds) {
    }
}
