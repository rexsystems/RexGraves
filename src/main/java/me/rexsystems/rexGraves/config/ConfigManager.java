package me.rexsystems.rexGraves.config;

import me.rexsystems.rexGraves.RexGraves;
import me.rexsystems.rexGraves.storage.GraveRepositoryFactory;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ConfigManager {

    private final RexGraves plugin;
    private FileConfiguration config;

    public ConfigManager(RexGraves plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        new ConfigAutoUpdater(plugin).update();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public void reload() {
        load();
    }

    public FileConfiguration raw() {
        return config;
    }

    public boolean isEnabled() {
        return config.getBoolean("settings.enabled", true);
    }

    public boolean storeExperience() {
        return config.getBoolean("settings.store-experience", true);
    }

    public int experiencePercent() {
        return Math.max(0, Math.min(100, config.getInt("settings.experience-percent", 100)));
    }

    public long expirationSeconds() {
        return Math.max(0L, config.getLong("settings.expiration-seconds", 3600L));
    }

    public String expireAction() {
        return config.getString("settings.expire-action", "drop").toLowerCase(Locale.ROOT);
    }

    public int maxGravesPerPlayer() {
        return Math.max(0, config.getInt("settings.max-graves-per-player", 3));
    }

    public boolean removeOldestWhenFull() {
        return config.getBoolean("settings.remove-oldest-when-full", true);
    }

    public boolean autoLoot() {
        return config.getBoolean("settings.auto-loot", false);
    }

    public boolean autoEquipArmor() {
        return config.getBoolean("settings.auto-equip-armor", true);
    }

    public boolean protectFromExplosions() {
        return config.getBoolean("settings.protect-from-explosions", true);
    }

    public boolean ownerOnly() {
        return config.getBoolean("settings.owner-only", true);
    }

    public boolean playerHead() {
        return config.getBoolean("settings.player-head", true);
    }

    public boolean smallMarker() {
        return config.getBoolean("settings.small-marker", true);
    }

    public boolean particles() {
        return config.getBoolean("settings.particles", true);
    }

    public String createSound() {
        return config.getString("settings.create-sound", "BLOCK_ENCHANTMENT_TABLE_USE");
    }

    public String lootSound() {
        return config.getString("settings.loot-sound", "BLOCK_CHEST_OPEN");
    }

    public String storageType() {
        return GraveRepositoryFactory.normalizeType(config.getString("storage.type", "yaml"));
    }

    public long autosaveSeconds() {
        return Math.max(0L, config.getLong("storage.autosave-seconds", 30L));
    }

    public String sqliteFile() {
        return config.getString("storage.sqlite.file", "graves.db");
    }

    public String mysqlHost() {
        return config.getString("storage.mysql.host", "127.0.0.1");
    }

    public int mysqlPort() {
        return Math.max(1, config.getInt("storage.mysql.port", 3306));
    }

    public String mysqlDatabase() {
        return config.getString("storage.mysql.database", "rexgraves");
    }

    public String mysqlUsername() {
        return config.getString("storage.mysql.username", "root");
    }

    public String mysqlPassword() {
        return config.getString("storage.mysql.password", "");
    }

    public int mysqlPoolSize() {
        return Math.max(1, config.getInt("storage.mysql.pool-size", 5));
    }

    public boolean mysqlUseSsl() {
        return config.getBoolean("storage.mysql.use-ssl", false);
    }

    public Set<String> blacklistedDeathCauses() {
        List<String> raw = config.getStringList("blacklisted-death-causes");
        if (raw == null || raw.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> causes = new HashSet<>();
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            causes.add(entry.trim().toUpperCase(Locale.ROOT));
        }
        return causes;
    }

    public boolean isDeathCauseBlacklisted(String causeName) {
        if (causeName == null || causeName.isBlank()) {
            return false;
        }
        return blacklistedDeathCauses().contains(causeName.toUpperCase(Locale.ROOT));
    }

    public List<String> disabledWorlds() {
        return config.getStringList("disabled-worlds");
    }

    public List<String> enabledWorlds() {
        return config.getStringList("enabled-worlds");
    }

    public boolean isWorldAllowed(String worldName) {
        List<String> enabled = enabledWorlds();
        if (enabled != null && !enabled.isEmpty()) {
            return enabled.stream().anyMatch(w -> w.equalsIgnoreCase(worldName));
        }
        List<String> disabled = disabledWorlds();
        if (disabled == null || disabled.isEmpty()) {
            return true;
        }
        return disabled.stream().noneMatch(w -> w.equalsIgnoreCase(worldName));
    }

    public boolean hologramEnabled() {
        return config.getBoolean("hologram.enabled", true);
    }

    public double hologramOffsetY() {
        return config.getDouble("hologram.offset-y", 1.35);
    }

    public double hologramLineSpacing() {
        return config.getDouble("hologram.line-spacing", 0.28);
    }

    public List<String> hologramLines() {
        List<String> lines = config.getStringList("hologram.lines");
        return lines == null ? Collections.emptyList() : lines;
    }

    public String guiTitle() {
        return config.getString("gui.title", "<gradient:#8B7355:#C4A574>{player}'s Grave</gradient>");
    }

    public int guiSize() {
        int size = config.getInt("gui.size", 54);
        if (size < 9) {
            return 9;
        }
        if (size > 54) {
            return 54;
        }
        return (int) (Math.ceil(size / 9.0) * 9);
    }

    public String message(String path) {
        return config.getString("messages." + path, "");
    }

    public String prefix() {
        return message("prefix");
    }

    public String prefixed(String path) {
        String raw = message(path);
        if (raw == null) {
            return "";
        }
        return raw.replace("<prefix>", prefix()).replace("{prefix}", prefix());
    }
}
