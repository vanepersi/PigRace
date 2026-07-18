package dev.genesi.baconbolt.model;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class Arena {

    private final String name;
    private StoredLocation lobby;
    private StoredLocation exit;
    /** Block players click to receive a join carrot. */
    private StoredLocation joinBlock;
    private final Map<String, RacePath> paths = new LinkedHashMap<>();

    public Arena(String name) {
        this.name = name.toLowerCase();
    }

    public String getName() {
        return name;
    }

    public Location getLobby() {
        return lobby == null ? null : lobby.toLocation();
    }

    public void setLobby(Location lobby) {
        this.lobby = StoredLocation.from(lobby);
    }

    public Location getExit() {
        return exit == null ? null : exit.toLocation();
    }

    public void setExit(Location exit) {
        this.exit = StoredLocation.from(exit);
    }

    public Location getJoinBlock() {
        return joinBlock == null ? null : joinBlock.toLocation();
    }

    public void setJoinBlock(Location joinBlock) {
        this.joinBlock = StoredLocation.from(joinBlock);
    }

    public boolean isJoinBlock(Location blockLoc) {
        if (joinBlock == null || blockLoc == null || blockLoc.getWorld() == null) {
            return false;
        }
        if (!joinBlock.getWorldName().equalsIgnoreCase(blockLoc.getWorld().getName())) {
            return false;
        }
        return joinBlock.getBlockX() == blockLoc.getBlockX()
                && joinBlock.getBlockY() == blockLoc.getBlockY()
                && joinBlock.getBlockZ() == blockLoc.getBlockZ();
    }

    public Collection<RacePath> getPaths() {
        return paths.values();
    }

    public Optional<RacePath> getPath(String pathName) {
        return Optional.ofNullable(paths.get(pathName.toLowerCase()));
    }

    public RacePath getOrCreatePath(String pathName) {
        return paths.computeIfAbsent(pathName.toLowerCase(), RacePath::new);
    }

    public boolean removePath(String pathName) {
        return paths.remove(pathName.toLowerCase()) != null;
    }

    public boolean isReady() {
        if (lobby == null || paths.isEmpty()) {
            return false;
        }
        for (RacePath path : paths.values()) {
            if (path.isReady()) {
                return true;
            }
        }
        return false;
    }

    public boolean areWorldsLoaded() {
        if (lobby != null && !lobby.isWorldLoaded()) {
            return false;
        }
        for (RacePath path : paths.values()) {
            if (path.isReady() && !path.areWorldsLoaded()) {
                return false;
            }
        }
        return true;
    }

    public Optional<RacePath> pickRandomReadyPath() {
        List<RacePath> ready = new ArrayList<>();
        for (RacePath path : paths.values()) {
            if (path.isReady() && path.areWorldsLoaded()) {
                ready.add(path);
            }
        }
        if (ready.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ready.get(ThreadLocalRandom.current().nextInt(ready.size())));
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (lobby != null) {
            map.put("lobby", lobby.serialize());
        }
        if (exit != null) {
            map.put("exit", exit.serialize());
        }
        if (joinBlock != null) {
            map.put("join-block", joinBlock.serialize());
        }
        Map<String, Object> pathMaps = new LinkedHashMap<>();
        for (Map.Entry<String, RacePath> entry : paths.entrySet()) {
            pathMaps.put(entry.getKey(), entry.getValue().serialize());
        }
        map.put("paths", pathMaps);
        return map;
    }

    public static Arena deserialize(String name, ConfigurationSection section) {
        Arena arena = new Arena(name);
        if (section == null) {
            return arena;
        }
        arena.lobby = StoredLocation.deserialize(section.getConfigurationSection("lobby"));
        if (arena.lobby == null && section.get("lobby") instanceof Map<?, ?> map) {
            arena.lobby = StoredLocation.deserializeMap(map);
        }
        arena.exit = StoredLocation.deserialize(section.getConfigurationSection("exit"));
        if (arena.exit == null && section.get("exit") instanceof Map<?, ?> map) {
            arena.exit = StoredLocation.deserializeMap(map);
        }
        arena.joinBlock = StoredLocation.deserialize(section.getConfigurationSection("join-block"));
        if (arena.joinBlock == null && section.get("join-block") instanceof Map<?, ?> map) {
            arena.joinBlock = StoredLocation.deserializeMap(map);
        }
        ConfigurationSection pathsSection = section.getConfigurationSection("paths");
        if (pathsSection != null) {
            for (String key : pathsSection.getKeys(false)) {
                arena.paths.put(key.toLowerCase(), RacePath.deserialize(key, pathsSection.getConfigurationSection(key)));
            }
        }
        return arena;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Arena arena)) {
            return false;
        }
        return Objects.equals(name, arena.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
