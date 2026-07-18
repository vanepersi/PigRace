package dev.genesi.baconbolt;

import dev.genesi.baconbolt.command.BaconBoltAdminCommand;
import dev.genesi.baconbolt.command.BaconBoltCommand;
import dev.genesi.baconbolt.listener.RaceListener;
import dev.genesi.baconbolt.manager.ArenaManager;
import dev.genesi.baconbolt.manager.GameManager;
import dev.genesi.baconbolt.manager.MessageService;
import dev.genesi.baconbolt.powerup.PowerUpRegistry;
import dev.genesi.baconbolt.stats.StatsService;
import dev.genesi.baconbolt.util.ItemFactory;
import org.bukkit.plugin.java.JavaPlugin;

public final class BaconBoltPlugin extends JavaPlugin {

    private ArenaManager arenaManager;
    private GameManager gameManager;
    private MessageService messageService;
    private ItemFactory itemFactory;
    private StatsService statsService;
    private PowerUpRegistry powerUpRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messageService = new MessageService(this);
        this.itemFactory = new ItemFactory(this);
        this.statsService = new StatsService(this);
        this.powerUpRegistry = new PowerUpRegistry(this);
        this.arenaManager = new ArenaManager(this);
        this.gameManager = new GameManager(this);

        arenaManager.load();
        gameManager.purgeRaceItemsOnline();

        BaconBoltCommand playerCommand = new BaconBoltCommand(this);
        BaconBoltAdminCommand adminCommand = new BaconBoltAdminCommand(this);
        getCommand("baconbolt").setExecutor(playerCommand);
        getCommand("baconbolt").setTabCompleter(playerCommand);
        getCommand("baconboltadmin").setExecutor(adminCommand);
        getCommand("baconboltadmin").setTabCompleter(adminCommand);

        getServer().getPluginManager().registerEvents(new RaceListener(this), this);

        getLogger().info("BaconBolt enabled — stats: " + statsService.provider().id()
                + ", power-ups: " + powerUpRegistry.all().size());
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        if (arenaManager != null) {
            arenaManager.save();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        messageService.reload();
        statsService.reload();
        powerUpRegistry.reload();
        arenaManager.load();
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public ItemFactory getItemFactory() {
        return itemFactory;
    }

    public StatsService getStatsService() {
        return statsService;
    }

    public PowerUpRegistry getPowerUpRegistry() {
        return powerUpRegistry;
    }
}
