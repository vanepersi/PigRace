package dev.genesi.pigrace.listener;

import dev.genesi.pigrace.PigRacePlugin;
import dev.genesi.pigrace.model.Arena;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RaceListener implements Listener {

    private final PigRacePlugin plugin;
    /** Prevents double-join if both interact + consume fire. */
    private final Set<UUID> joinCooldown = ConcurrentHashMap.newKeySet();

    public RaceListener(PigRacePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Force-join on right-click even when the player is full in survival
     * (vanilla refuses to eat food at max hunger).
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

        // Stop vanilla eat rules (full hunger, etc.) — we handle it ourselves
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);

        attemptJoinFromCarrot(player, held, hand, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEatJoinCarrot(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!plugin.getItemFactory().isJoinItem(item)) {
            return;
        }
        // Interact handler already consumes + joins; skip duplicate if it raced here
        if (joinCooldown.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        attemptJoinFromCarrot(event.getPlayer(), item, EquipmentSlot.HAND, false);
    }

    private void attemptJoinFromCarrot(Player player, ItemStack stack, EquipmentSlot hand, boolean consumeItem) {
        if (!joinCooldown.add(player.getUniqueId())) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> joinCooldown.remove(player.getUniqueId()), 20L);

        if (!player.hasPermission("pigrace.use")) {
            plugin.getMessageService().send(player, "no-permission");
            return;
        }
        String arenaName = plugin.getItemFactory().getJoinArena(stack);
        Optional<Arena> arena = plugin.getArenaManager().get(arenaName);
        if (arena.isEmpty()) {
            plugin.getMessageService().send(player, "arena-not-found", Map.of("arena", arenaName));
            return;
        }

        if (consumeItem) {
            ItemStack held = player.getInventory().getItem(hand);
            if (plugin.getItemFactory().isJoinItem(held)) {
                if (held.getAmount() <= 1) {
                    player.getInventory().setItem(hand, null);
                } else {
                    held.setAmount(held.getAmount() - 1);
                    player.getInventory().setItem(hand, held);
                }
            }
            player.swingHand(hand);
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
        }

        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getGameManager().tryJoin(player, arena.get()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        Entity rider = event.getEntity();
        if (!(rider instanceof Player player)) {
            return;
        }
        if (!plugin.getGameManager().isPlaying(player.getUniqueId())) {
            return;
        }
        plugin.getGameManager().tryRemount(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        joinCooldown.remove(event.getPlayer().getUniqueId());
        plugin.getGameManager().leave(event.getPlayer(), false);
    }
}
