package dev.genesi.pigrace.util;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class ItemFactory {

    public static final String JOIN_ITEM_KEY = "join_item";
    public static final String STEER_ITEM_KEY = "steer_item";

    private final JavaPlugin plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey arenaKey;

    public ItemFactory(JavaPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "pigrace");
        this.arenaKey = new NamespacedKey(plugin, "arena");
    }

    public ItemStack createJoinItem() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("join-item");
        Material material = Material.CARROT;
        String display = "&6&lPig Race Carrot";
        List<String> lore = List.of("&7Eat to join the lounge pig race!");
        int cmd = 0;
        String itemModel = "";
        String arena = "lounge";
        if (section != null) {
            Material matched = Material.matchMaterial(section.getString("material", "CARROT"));
            material = matched == null ? Material.CARROT : matched;
            display = section.getString("display-name", display);
            lore = section.getStringList("lore");
            cmd = section.getInt("custom-model-data", 0);
            itemModel = section.getString("item-model", "");
            arena = section.getString("arena", "lounge");
        }
        return buildMarked(material, display, lore, cmd, itemModel, JOIN_ITEM_KEY, arena);
    }

    public ItemStack createSteerItem() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("steer-item");
        Material material = Material.CARROT_ON_A_STICK;
        String display = "&d&lPig Stick";
        List<String> lore = List.of("&7Hold to steer your pig!");
        int cmd = 0;
        String itemModel = "";
        if (section != null) {
            Material matched = Material.matchMaterial(section.getString("material", "CARROT_ON_A_STICK"));
            material = matched == null ? Material.CARROT_ON_A_STICK : matched;
            display = section.getString("display-name", display);
            lore = section.getStringList("lore");
            cmd = section.getInt("custom-model-data", 0);
            itemModel = section.getString("item-model", "");
        }
        return buildMarked(material, display, lore, cmd, itemModel, STEER_ITEM_KEY, null);
    }

    private ItemStack buildMarked(
            Material material,
            String display,
            List<String> lore,
            int cmd,
            String itemModel,
            String marker,
            String arena
    ) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(display));
        if (lore != null && !lore.isEmpty()) {
            List<net.kyori.adventure.text.Component> components = new ArrayList<>();
            for (String line : lore) {
                components.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
            }
            meta.lore(components);
        }
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        if (itemModel != null && !itemModel.isBlank()) {
            try {
                NamespacedKey modelKey = NamespacedKey.fromString(itemModel);
                if (modelKey != null) {
                    meta.setItemModel(modelKey);
                }
            } catch (NoSuchMethodError ignored) {
            }
        }
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, marker);
        if (arena != null) {
            meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, arena.toLowerCase());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isJoinItem(ItemStack stack) {
        return hasMarker(stack, JOIN_ITEM_KEY);
    }

    public boolean isSteerItem(ItemStack stack) {
        return hasMarker(stack, STEER_ITEM_KEY);
    }

    private boolean hasMarker(ItemStack stack, String expected) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String value = stack.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
        return expected.equals(value);
    }

    public String getJoinArena(ItemStack stack) {
        if (!isJoinItem(stack)) {
            return plugin.getConfig().getString("join-item.arena", "lounge");
        }
        String arena = stack.getItemMeta().getPersistentDataContainer().get(arenaKey, PersistentDataType.STRING);
        if (arena == null || arena.isBlank()) {
            return plugin.getConfig().getString("join-item.arena", "lounge");
        }
        return arena.toLowerCase();
    }
}
