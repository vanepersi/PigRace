package dev.genesi.baconbolt.powerup;

import org.bukkit.potion.PotionEffectType;

/**
 * One weighted Mario Kart–style effect from config.
 */
public record PowerUpEffect(
        String id,
        String display,
        int weight,
        PotionEffectType potion,
        int amplifier,
        int durationTicks
) {
}
