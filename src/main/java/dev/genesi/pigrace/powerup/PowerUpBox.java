package dev.genesi.pigrace.powerup;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Runtime rotating power-up box tied to a path spawn location.
 */
public final class PowerUpBox {

    private final int index;
    private final Location base;
    private ItemDisplay display;
    private boolean available = true;
    private long respawnAtMillis;
    private float yaw;

    public PowerUpBox(int index, Location base) {
        this.index = index;
        this.base = base.clone();
    }

    public int getIndex() {
        return index;
    }

    public Location getBase() {
        return base.clone();
    }

    public ItemDisplay getDisplay() {
        return display;
    }

    public void setDisplay(ItemDisplay display) {
        this.display = display;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public long getRespawnAtMillis() {
        return respawnAtMillis;
    }

    public void setRespawnAtMillis(long respawnAtMillis) {
        this.respawnAtMillis = respawnAtMillis;
    }

    public float getYaw() {
        return yaw;
    }

    public void addYaw(float degrees) {
        this.yaw = (yaw + degrees) % 360f;
    }

    public void applyTransform(float bobY, float scale) {
        if (display == null || display.isDead()) {
            return;
        }
        Transformation t = new Transformation(
                new Vector3f(0f, bobY, 0f),
                new AxisAngle4f((float) Math.toRadians(yaw), 0f, 1f, 0f),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0f, 0f, 1f, 0f)
        );
        display.setTransformation(t);
    }

    public void remove() {
        if (display != null && !display.isDead()) {
            display.remove();
        }
        display = null;
    }
}
