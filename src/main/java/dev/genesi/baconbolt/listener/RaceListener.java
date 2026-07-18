package dev.genesi.baconbolt.listener;

import dev.genesi.baconbolt.BaconBoltPlugin;
import dev.genesi.baconbolt.model.Arena;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RaceListener implements Listener {

    private final BaconBoltPlugin plugin;
    private final Set<UUID> joinCooldown = ConcurrentHashMap.newKeySet();

    public RaceListener(BaconBoltPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Force-join on right-click even at full hunger.
     * Join carrots are stripped from inventory only after a successful join.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUseJoinCarrot(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(hand);
        if (!plugin.getItemFactory().isJoinItem(held)) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);

        attemptJoinFromCarrot(player, held, hand);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEatJoinCarrot(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!plugin.getItemFactory().isJoinItem(item)) {
            return;
        }
        // Cancel vanilla consume — interact path (or this) handles join + inventory clear
        event.setCancelled(true);
        if (joinCooldown.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        attemptJoinFromCarrot(event.getPlayer(), item, EquipmentSlot.HAND);
    }

    private void attemptJoinFromCarrot(Player player, ItemStack stack, EquipmentSlot hand) {
        if (!joinCooldown.add(player.getUniqueId())) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> joinCooldown.remove(player.getUniqueId()), 20L);

        if (!player.hasPermission("baconbolt.use")) {
            plugin.getMessageService().send(player, "no-permission");
            return;
        }
        String arenaName = plugin.getItemFactory().getJoinArena(stack);
        Optional<Arena> arena = plugin.getArenaManager().get(arenaName);
        if (arena.isEmpty()) {
            plugin.getMessageService().send(player, "arena-not-found", Map.of("arena", arenaName));
            return;
        }

        player.swingHand(hand);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.getGameManager().tryJoin(player, arena.get())) {
                // Strip every join carrot so nothing lingers after restart / leave
                plugin.getItemFactory().clearJoinItems(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDismount(EntityDismountEvent event) {
        Entity rider = event.getEntity();
        if (!(rider instanceof Player player)) {
            return;
        }
        if (!plugin.getGameManager().isPlaying(player.getUniqueId())) {
            return;
        }
        if (plugin.getGameManager().canDismount(player.getUniqueId())) {
            return;
        }
        // Never allow dismount while in a race session
        event.setCancelled(true);
        plugin.getGameManager().tryRemount(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.getGameManager().isPlaying(player.getUniqueId())) {
            return;
        }
        if (plugin.getGameManager().canDismount(player.getUniqueId())) {
            return;
        }
        if (player.getVehicle() != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        joinCooldown.remove(event.getPlayer().getUniqueId());
        plugin.getGameManager().leave(event.getPlayer(), false);
        plugin.getItemFactory().clearRaceItems(event.getPlayer());
    }
}
