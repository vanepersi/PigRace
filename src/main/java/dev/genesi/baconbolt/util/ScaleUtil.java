package dev.genesi.baconbolt.util;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

public final class ScaleUtil {

    private ScaleUtil() {
    }

    public static double getScale(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attribute.SCALE);
        return instance == null ? 1.0 : instance.getBaseValue();
    }

    public static void setScale(LivingEntity entity, double scale) {
        AttributeInstance instance = entity.getAttribute(Attribute.SCALE);
        if (instance != null) {
            instance.setBaseValue(Math.max(0.06, scale));
        }
    }
}
