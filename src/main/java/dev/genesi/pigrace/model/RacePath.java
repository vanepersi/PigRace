package dev.genesi.pigrace.model;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One race route inside a lounge — spawns + finish volume.
 * Arenas hold several paths; a random path is chosen each round.
 */
public final class RacePath {

    private final String name;
    private final List<StoredLocation> spawns = new ArrayList<>();
    private StoredLocation finishA;
    private StoredLocation finishB;

    public RacePath(String name) {
        this.name = name.toLowerCase();
    }

    public String getName() {
        return name;
    }

    public List<StoredLocation> getStoredSpawns() {
        return List.copyOf(spawns);
    }

    public List<Location> getSpawns() {
        List<Location> resolved = new ArrayList<>(spawns.size());
        for (StoredLocation spawn : spawns) {
            Location location = spawn.toLocation();
            if (location != null) {
                resolved.add(location);
            }
        }
        return resolved;
    }

    public int addSpawn(Location location) {
        StoredLocation stored = StoredLocation.from(location);
        if (stored == null) {
            return -1;
        }
        spawns.add(stored);
        return spawns.size() - 1;
    }

    public void clearSpawns() {
        spawns.clear();
    }

    public void setFinishA(Location location) {
        this.finishA = StoredLocation.from(location);
    }

    public void setFinishB(Location location) {
        this.finishB = StoredLocation.from(location);
    }

    public StoredLocation getFinishA() {
        return finishA;
    }

    public StoredLocation getFinishB() {
        return finishB;
    }

    public boolean isReady() {
        return !spawns.isEmpty() && finishA != null && finishB != null;
    }

    public boolean areWorldsLoaded() {
        for (StoredLocation spawn : spawns) {
            if (!spawn.isWorldLoaded()) {
                return false;
            }
        }
        return (finishA == null || finishA.isWorldLoaded())
                && (finishB == null || finishB.isWorldLoaded());
    }

    /**
     * Axis-aligned finish volume from the two corner points.
     * If only one corner resolves, falls back to a sphere check via center.
     */
    public BoundingBox finishBox() {
        Location a = finishA == null ? null : finishA.toLocation();
        Location b = finishB == null ? null : finishB.toLocation();
        if (a == null || b == null) {
            return null;
        }
        return BoundingBox.of(a, b);
    }

    public Location finishCenter() {
        Location a = finishA == null ? null : finishA.toLocation();
        Location b = finishB == null ? null : finishB.toLocation();
        if (a == null && b == null) {
            return null;
        }
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.clone().add(b).multiply(0.5);
    }

    public boolean contains(Location location, double radiusFallback) {
        if (location == null) {
            return false;
        }
        BoundingBox box = finishBox();
        if (box != null) {
            return box.contains(location.toVector());
        }
        Location center = finishCenter();
        if (center == null || center.getWorld() == null || location.getWorld() == null) {
            return false;
        }
        if (!center.getWorld().equals(location.getWorld())) {
            return false;
        }
        return center.distanceSquared(location) <= radiusFallback * radiusFallback;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        List<Map<String, Object>> spawnMaps = new ArrayList<>();
        for (StoredLocation spawn : spawns) {
            spawnMaps.add(spawn.serialize());
        }
        map.put("spawns", spawnMaps);
        if (finishA != null) {
            map.put("finish-a", finishA.serialize());
        }
        if (finishB != null) {
            map.put("finish-b", finishB.serialize());
        }
        return map;
    }

    public static RacePath deserialize(String name, ConfigurationSection section) {
        RacePath path = new RacePath(name);
        if (section == null) {
            return path;
        }
        List<?> rawSpawns = section.getList("spawns");
        if (rawSpawns != null) {
            for (Object entry : rawSpawns) {
                if (entry instanceof Map<?, ?> map) {
                    StoredLocation stored = StoredLocation.deserializeMap(map);
                    if (stored != null) {
                        path.spawns.add(stored);
                    }
                } else if (entry instanceof ConfigurationSection nested) {
                    StoredLocation stored = StoredLocation.deserialize(nested);
                    if (stored != null) {
                        path.spawns.add(stored);
                    }
                }
            }
        }
        path.finishA = StoredLocation.deserialize(section.getConfigurationSection("finish-a"));
        if (path.finishA == null && section.get("finish-a") instanceof Map<?, ?> map) {
            path.finishA = StoredLocation.deserializeMap(map);
        }
        path.finishB = StoredLocation.deserialize(section.getConfigurationSection("finish-b"));
        if (path.finishB == null && section.get("finish-b") instanceof Map<?, ?> map) {
            path.finishB = StoredLocation.deserializeMap(map);
        }
        return path;
    }
}
