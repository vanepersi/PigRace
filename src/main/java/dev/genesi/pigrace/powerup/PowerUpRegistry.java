package dev.genesi.pigrace.powerup;

import dev.genesi.pigrace.PigRacePlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class PowerUpRegistry {

    private final PigRacePlugin plugin;
    private final List<PowerUpEffect> effects = new ArrayList<>();

    public PowerUpRegistry(PigRacePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        effects.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("powerups.effects");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection effect = section.getConfigurationSection(key);
            if (effect == null) {
                continue;
            }
            String potionName = effect.getString("potion", "SPEED");
            PotionEffectType type = resolvePotion(potionName);
            if (type == null) {
                plugin.getLogger().warning("Unknown power-up potion: " + potionName + " (" + key + ")");
                continue;
            }
            effects.add(new PowerUpEffect(
                    key.toLowerCase(Locale.ROOT),
                    effect.getString("display", "&e" + key),
                    Math.max(1, effect.getInt("weight", 10)),
                    type,
                    effect.getInt("amplifier", 0),
                    Math.max(1, effect.getInt("duration-ticks", 60))
            ));
        }
    }

    public List<PowerUpEffect> all() {
        return List.copyOf(effects);
    }

    public PowerUpEffect pickWeighted() {
        if (effects.isEmpty()) {
            return null;
        }
        int total = 0;
        for (PowerUpEffect effect : effects) {
            total += effect.weight();
        }
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        int cursor = 0;
        for (PowerUpEffect effect : effects) {
            cursor += effect.weight();
            if (roll < cursor) {
                return effect;
            }
        }
        return effects.getLast();
    }

    public PowerUpEffect pickRandomForSpin() {
        if (effects.isEmpty()) {
            return null;
        }
        return effects.get(ThreadLocalRandom.current().nextInt(effects.size()));
    }

    public Material boxMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("powerups.material", "GOLD_BLOCK"));
        return material == null ? Material.GOLD_BLOCK : material;
    }

    private PotionEffectType resolvePotion(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (key.equals("jump")) {
            key = "jump_boost";
        }
        if (key.equals("confusion")) {
            key = "nausea";
        }
        PotionEffectType byName = PotionEffectType.getByName(key.toUpperCase(Locale.ROOT));
        if (byName != null) {
            return byName;
        }
        return PotionEffectType.getByKey(NamespacedKey.minecraft(key));
    }
}
