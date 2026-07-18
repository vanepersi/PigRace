package dev.genesi.pigrace.listener;

import dev.genesi.pigrace.PigRacePlugin;
import dev.genesi.pigrace.model.Arena;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

public final class RaceListener implements Listener {

    private final PigRacePlugin plugin;

    public RaceListener(PigRacePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEatJoinCarrot(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!plugin.getItemFactory().isJoinItem(item)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("pigrace.use")) {
            event.setCancelled(true);
            plugin.getMessageService().send(player, "no-permission");
            return;
        }
        String arenaName = plugin.getItemFactory().getJoinArena(item);
        Optional<Arena> arena = plugin.getArenaManager().get(arenaName);
        if (arena.isEmpty()) {
            event.setCancelled(true);
            plugin.getMessageService().send(player, "arena-not-found", Map.of("arena", arenaName));
            return;
        }
        // Allow the eat animation to play; join after a tick so the consume finishes
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
        plugin.getGameManager().leave(event.getPlayer(), false);
    }
}
