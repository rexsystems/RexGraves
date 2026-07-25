package me.rexsystems.rexGraves.config;

import me.rexsystems.rexGraves.RexGraves;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Merges missing defaults from the bundled config.yml into the user's config
 * without overwriting existing values. Tracks config-version and creates a
 * timestamped backup when changes are written.
 */
public class ConfigAutoUpdater {

    public static final int CURRENT_CONFIG_VERSION = 1;

    private final RexGraves plugin;

    public ConfigAutoUpdater(RexGraves plugin) {
        this.plugin = plugin;
    }

    public void update() {
        try {
            FileConfiguration userCfg = plugin.getConfig();
            InputStream is = plugin.getResource("config.yml");
            if (is == null) {
                plugin.getLogger().warning("Default config.yml resource not found; skipping auto-update.");
                return;
            }

            YamlConfiguration defaults = YamlConfiguration
                    .loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));

            int fromVersion = userCfg.getInt("config-version", 0);
            int missing = mergeMissingKeys(userCfg, defaults);
            boolean migrated = migrate(userCfg, fromVersion);

            if (missing <= 0 && !migrated && fromVersion >= CURRENT_CONFIG_VERSION) {
                return;
            }

            if (userCfg.getInt("config-version", 0) < CURRENT_CONFIG_VERSION) {
                userCfg.set("config-version", CURRENT_CONFIG_VERSION);
            }

            backupConfigSafely();
            plugin.saveConfig();

            if (fromVersion < CURRENT_CONFIG_VERSION) {
                plugin.getLogger().info("Config updated from v" + fromVersion + " to v" + CURRENT_CONFIG_VERSION
                        + (missing > 0 ? " (added " + missing + " missing key(s))" : "") + ".");
            } else if (missing > 0) {
                plugin.getLogger().info("Config auto-update: added " + missing + " missing key(s).");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Config auto-update failed: " + t.getMessage());
        }
    }

    private int mergeMissingKeys(FileConfiguration userCfg, YamlConfiguration defaults) {
        int count = 0;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            if (userCfg.isSet(key)) {
                continue;
            }
            userCfg.set(key, defaults.get(key));
            count++;
        }
        return count;
    }

    /**
     * Version-specific migrations. Preserve user values unless a safe structural fix is needed.
     */
    private boolean migrate(FileConfiguration userCfg, int fromVersion) {
        boolean changed = false;

        if (fromVersion < 1) {
            // Older holograms lacked time_since; refresh only the stock 3-line template.
            List<String> lines = userCfg.getStringList("hologram.lines");
            if (lines != null && lines.size() == 3
                    && lines.stream().noneMatch(line -> line != null && line.contains("{time_since}"))) {
                boolean looksDefault =
                        lines.get(0) != null && lines.get(0).contains("{player}'s Grave")
                                && lines.get(1) != null && lines.get(1).contains("{items}")
                                && lines.get(2) != null && lines.get(2).contains("{time_left}");
                if (looksDefault) {
                    List<String> updated = new ArrayList<>();
                    updated.add("<gradient:#8B7355:#C4A574><bold>{player}'s Grave</bold></gradient>");
                    updated.add("<gray>Items: <white>{items}</white> · XP: <white>{xp}</white></gray>");
                    updated.add("<gray>Died <white>{time_since}</white> ago</gray>");
                    updated.add("<dark_gray>Expires in {time_left}</dark_gray>");
                    userCfg.set("hologram.lines", updated);
                    changed = true;
                }
            }
        }

        return changed;
    }

    private void backupConfigSafely() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                return;
            }
            File file = new File(dataFolder, "config.yml");
            if (!file.exists()) {
                return;
            }
            String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            Path backup = new File(dataFolder, "config.yml.bak." + ts).toPath();
            Files.copy(file.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ignored) {
        }
    }
}
