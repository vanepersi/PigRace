package dev.genesi.pigrace;

import dev.genesi.pigrace.command.PigRaceAdminCommand;
import dev.genesi.pigrace.command.PigRaceCommand;
import dev.genesi.pigrace.listener.RaceListener;
import dev.genesi.pigrace.manager.ArenaManager;
import dev.genesi.pigrace.manager.GameManager;
import dev.genesi.pigrace.manager.MessageService;
import dev.genesi.pigrace.stats.StatsService;
import dev.genesi.pigrace.util.ItemFactory;
import org.bukkit.plugin.java.JavaPlugin;

public final class PigRacePlugin extends JavaPlugin {

    private ArenaManager arenaManager;
    private GameManager gameManager;
    private MessageService messageService;
    private ItemFactory itemFactory;
    private StatsService statsService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messageService = new MessageService(this);
        this.itemFactory = new ItemFactory(this);
        this.statsService = new StatsService(this);
        this.arenaManager = new ArenaManager(this);
        this.gameManager = new GameManager(this);

        arenaManager.load();

        PigRaceCommand playerCommand = new PigRaceCommand(this);
        PigRaceAdminCommand adminCommand = new PigRaceAdminCommand(this);
        getCommand("pigrace").setExecutor(playerCommand);
        getCommand("pigrace").setTabCompleter(playerCommand);
        getCommand("pigraceadmin").setExecutor(adminCommand);
        getCommand("pigraceadmin").setTabCompleter(adminCommand);

        getServer().getPluginManager().registerEvents(new RaceListener(this), this);

        getLogger().info("PigRace enabled — stats provider: " + statsService.provider().id());
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
}
