package dev.genesi.baconbolt.manager;

import dev.genesi.baconbolt.model.Arena;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class ArenaManager {

    private final JavaPlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private File file;
    private FileConfiguration data;

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        arenas.clear();
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create data folder");
        }
        file = new File(plugin.getDataFolder(), "arenas.yml");
        if (!file.exists()) {
            plugin.saveResource("arenas.yml", false);
        }
        data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("arenas");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                arenas.put(key.toLowerCase(), Arena.deserialize(key, section.getConfigurationSection(key)));
            }
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " pig race arena(s).");
    }

    public void save() {
        if (data == null) {
            data = new YamlConfiguration();
        }
        data.set("arenas", null);
        for (Map.Entry<String, Arena> entry : arenas.entrySet()) {
            data.createSection("arenas." + entry.getKey(), entry.getValue().serialize());
        }
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save arenas.yml", ex);
        }
    }

    public Optional<Arena> get(String name) {
        return Optional.ofNullable(arenas.get(name.toLowerCase()));
    }

    public Arena create(String name) {
        Arena arena = new Arena(name);
        arenas.put(arena.getName(), arena);
        save();
        return arena;
    }

    public boolean delete(String name) {
        if (arenas.remove(name.toLowerCase()) != null) {
            save();
            return true;
        }
        return false;
    }

    public Collection<Arena> all() {
        return arenas.values();
    }
}
