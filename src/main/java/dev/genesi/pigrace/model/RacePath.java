package dev.genesi.pigrace.model;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One race route inside a lounge — spawns, trail waypoints, power-up boxes, finish volume.
 */
public final class RacePath {

    private final String name;
    private final List<StoredLocation> spawns = new ArrayList<>();
    private final List<StoredLocation> trail = new ArrayList<>();
    private final List<StoredLocation> powerUps = new ArrayList<>();
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
        return resolve(spawns);
    }

    public int addSpawn(Location location) {
        return add(spawns, location);
    }

    public void clearSpawns() {
        spawns.clear();
    }

    public List<Location> getTrail() {
        return resolve(trail);
    }

    public int addTrail(Location location) {
        return add(trail, location);
    }

    public void clearTrail() {
        trail.clear();
    }

    public List<Location> getPowerUps() {
        return resolve(powerUps);
    }

    public int addPowerUp(Location location) {
        return add(powerUps, location);
    }

    public void clearPowerUps() {
        powerUps.clear();
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
        for (StoredLocation point : trail) {
            if (!point.isWorldLoaded()) {
                return false;
            }
        }
        for (StoredLocation box : powerUps) {
            if (!box.isWorldLoaded()) {
                return false;
            }
        }
        return (finishA == null || finishA.isWorldLoaded())
                && (finishB == null || finishB.isWorldLoaded());
    }

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
        map.put("spawns", serializeList(spawns));
        map.put("trail", serializeList(trail));
        map.put("powerups", serializeList(powerUps));
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
        readList(section.getList("spawns"), path.spawns);
        readList(section.getList("trail"), path.trail);
        readList(section.getList("powerups"), path.powerUps);
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

    private static int add(List<StoredLocation> list, Location location) {
        StoredLocation stored = StoredLocation.from(location);
        if (stored == null) {
            return -1;
        }
        list.add(stored);
        return list.size() - 1;
    }

    private static List<Location> resolve(List<StoredLocation> stored) {
        List<Location> resolved = new ArrayList<>(stored.size());
        for (StoredLocation point : stored) {
            Location location = point.toLocation();
            if (location != null) {
                resolved.add(location);
            }
        }
        return resolved;
    }

    private static List<Map<String, Object>> serializeList(List<StoredLocation> list) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (StoredLocation point : list) {
            maps.add(point.serialize());
        }
        return maps;
    }

    private static void readList(List<?> raw, List<StoredLocation> target) {
        if (raw == null) {
            return;
        }
        for (Object entry : raw) {
            if (entry instanceof Map<?, ?> map) {
                StoredLocation stored = StoredLocation.deserializeMap(map);
                if (stored != null) {
                    target.add(stored);
                }
            } else if (entry instanceof ConfigurationSection nested) {
                StoredLocation stored = StoredLocation.deserialize(nested);
                if (stored != null) {
                    target.add(stored);
                }
            }
        }
    }
}
