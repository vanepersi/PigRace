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
        Material material = Material.CARROT_ON_A_STICK;
        String display = "&d&lPig Race";
        List<String> lore = List.of("&7Click to join the lounge pig race!");
        int cmd = 0;
        String itemModel = "";
        String arena = "lounge";
        if (section != null) {
            material = Material.matchMaterial(section.getString("material", "CARROT_ON_A_STICK"));
            if (material == null) {
                material = Material.CARROT_ON_A_STICK;
            }
            display = section.getString("display-name", display);
            lore = section.getStringList("lore");
            cmd = section.getInt("custom-model-data", 0);
            itemModel = section.getString("item-model", "");
            arena = section.getString("arena", "lounge");
        }

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
                // Older API without item model — CMD still works
            }
        }
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, JOIN_ITEM_KEY);
        meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, arena == null ? "lounge" : arena.toLowerCase());
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isJoinItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String value = stack.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
        return JOIN_ITEM_KEY.equals(value);
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

    public NamespacedKey getMarkerKey() {
        return markerKey;
    }
}
