package me.rexsystems.rexGraves.storage;

import me.rexsystems.rexGraves.RexGraves;

import java.util.Locale;

public final class GraveRepositoryFactory {

    private GraveRepositoryFactory() {
    }

    public static GraveRepository create(RexGraves plugin) {
        String type = plugin.getConfigManager().storageType();
        return switch (type) {
            case "sqlite" -> {
                plugin.getLogger().info("Using SQLite grave storage.");
                yield new SqliteGraveRepository(plugin);
            }
            case "mysql" -> {
                plugin.getLogger().info("Using MySQL grave storage.");
                yield new MysqlGraveRepository(plugin);
            }
            default -> {
                if (!"yaml".equals(type)) {
                    plugin.getLogger().warning("Unknown storage.type '" + type + "', falling back to yaml.");
                }
                plugin.getLogger().info("Using YAML grave storage.");
                yield new YamlGraveRepository(plugin);
            }
        };
    }

    public static String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "yaml";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
