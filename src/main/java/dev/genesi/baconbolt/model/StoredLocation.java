package dev.genesi.baconbolt.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Location that keeps the world name even when the world is not loaded yet
 * (common with Multiverse — worlds often load after plugins enable).
 */
public final class StoredLocation {

    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public StoredLocation(String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static StoredLocation from(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new StoredLocation(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public String getWorldName() {
        return worldName;
    }

    public Location toLocation() {
        if (worldName == null) {
            return null;
        }
        try {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return null;
            }
            return new Location(world, x, y, z, yaw, pitch);
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException ex) {
            return null;
        }
    }

    public boolean isWorldLoaded() {
        if (worldName == null) {
            return false;
        }
        try {
            return Bukkit.getWorld(worldName) != null;
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException ex) {
            return false;
        }
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", worldName);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("yaw", yaw);
        map.put("pitch", pitch);
        return map;
    }

    public static StoredLocation deserialize(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return deserializeMap(section.getValues(false));
    }

    public static StoredLocation deserializeMap(Map<?, ?> map) {
        if (map == null || !map.containsKey("world")) {
            return null;
        }
        Object worldObj = map.get("world");
        if (!(worldObj instanceof String worldName) || worldName.isBlank()) {
            return null;
        }
        return new StoredLocation(
                worldName,
                toDouble(map.get("x")),
                toDouble(map.get("y")),
                toDouble(map.get("z")),
                (float) toDouble(map.get("yaw")),
                (float) toDouble(map.get("pitch"))
        );
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
