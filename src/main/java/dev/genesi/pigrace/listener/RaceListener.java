package dev.genesi.pigrace.listener;

import dev.genesi.pigrace.PigRacePlugin;
import dev.genesi.pigrace.model.Arena;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class RaceListener implements Listener {

    private final PigRacePlugin plugin;

    public RaceListener(PigRacePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onJoinItem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!plugin.getItemFactory().isJoinItem(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("pigrace.use")) {
            plugin.getMessageService().send(player, "no-permission");
            return;
        }
        String arenaName = plugin.getItemFactory().getJoinArena(item);
        Optional<Arena> arena = plugin.getArenaManager().get(arenaName);
        if (arena.isEmpty()) {
            plugin.getMessageService().send(player, "arena-not-found", MapOf.arena(arenaName));
            return;
        }
        plugin.getGameManager().tryJoin(player, arena.get());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGameManager().leave(event.getPlayer(), false);
    }

    /** Tiny helper to avoid Map.of import noise for a single placeholder. */
    private static final class MapOf {
        static java.util.Map<String, String> arena(String name) {
            return java.util.Map.of("arena", name);
        }
    }
}
