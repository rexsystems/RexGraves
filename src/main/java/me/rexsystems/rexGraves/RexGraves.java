package me.rexsystems.rexGraves;

import me.rexsystems.rexGraves.command.GravesCommand;
import me.rexsystems.rexGraves.config.ConfigManager;
import me.rexsystems.rexGraves.grave.GraveManager;
import me.rexsystems.rexGraves.listener.DeathListener;
import me.rexsystems.rexGraves.listener.GraveListener;
import me.rexsystems.rexGraves.util.SchedulerUtils;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RexGraves extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 32888;
    private static RexGraves instance;

    private ConfigManager configManager;
    private GraveManager graveManager;

    @Override
    public void onEnable() {
        instance = this;

        try {
            this.configManager = new ConfigManager(this);
            this.configManager.load();

            this.graveManager = new GraveManager(this);
            this.graveManager.load();

            getServer().getPluginManager().registerEvents(new DeathListener(this), this);
            getServer().getPluginManager().registerEvents(new GraveListener(this), this);

            GravesCommand command = new GravesCommand(this);
            PluginCommand pluginCommand = getCommand("rexgraves");
            if (pluginCommand != null) {
                pluginCommand.setExecutor(command);
                pluginCommand.setTabCompleter(command);
            }

            // Tick graves every 5 seconds (100 ticks)
            SchedulerUtils.runAtFixedRate(this, () -> graveManager.tick(), 100L, 100L);

            new Metrics(this, BSTATS_PLUGIN_ID);

            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new me.rexsystems.rexGraves.papi.RexGravesPlaceholders(this).register();
                getLogger().info("PlaceholderAPI expansion registered!");
            }

            getLogger().info("RexGraves has been enabled successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to enable RexGraves: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (graveManager != null) {
            graveManager.shutdown();
        }
        getLogger().info("RexGraves has been disabled!");
    }

    public void reloadPlugin() {
        configManager.reload();
    }

    public static RexGraves getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GraveManager getGraveManager() {
        return graveManager;
    }
}
