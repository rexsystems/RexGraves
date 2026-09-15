package me.rexsystems.rexGraves.grave;

import me.rexsystems.rexGraves.RexGraves;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

public class GraveStorage {

    private final RexGraves plugin;
    private final File file;
    private FileConfiguration data;

    public GraveStorage(RexGraves plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "graves.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    plugin.getLogger().warning("Could not create graves.yml");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create graves.yml", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public List<Grave> readAll() {
        List<Grave> graves = new ArrayList<>();
        ConfigurationSection root = data.getConfigurationSection("graves");
        if (root == null) {
            return graves;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                graves.add(Grave.load(section));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load grave " + key, e);
            }
        }
        return graves;
    }

    public void saveAll(Collection<Grave> graves) {
        data.set("graves", null);
        ConfigurationSection root = data.createSection("graves");
        for (Grave grave : graves) {
            ConfigurationSection section = root.createSection(grave.getId());
            grave.save(section);
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save graves.yml", e);
        }
    }
}
