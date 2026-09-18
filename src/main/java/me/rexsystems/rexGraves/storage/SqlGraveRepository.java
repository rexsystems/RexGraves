package me.rexsystems.rexGraves.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.rexsystems.rexGraves.RexGraves;
import me.rexsystems.rexGraves.grave.Grave;
import me.rexsystems.rexGraves.util.ItemSerializer;
import me.rexsystems.rexGraves.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public abstract class SqlGraveRepository implements GraveRepository {

    protected static final String TABLE = "rexgraves_graves";

    protected final RexGraves plugin;
    protected final HikariDataSource dataSource;
    private final Object saveLock = new Object();

    protected SqlGraveRepository(RexGraves plugin, HikariDataSource dataSource) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        createTable();
    }

    protected abstract String upsertSql();

    protected static HikariDataSource createSqlitePool(RexGraves plugin, File dbFile) {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("RexGraves-SQLite");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(10_000L);
        config.addDataSourceProperty("journal_mode", "WAL");
        return new HikariDataSource(config);
    }

    protected static HikariDataSource createMysqlPool(RexGraves plugin) {
        var cfg = plugin.getConfigManager();
        HikariConfig config = new HikariConfig();
        config.setPoolName("RexGraves-MySQL");
        config.setJdbcUrl(String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=true&characterEncoding=utf8",
                cfg.mysqlHost(),
                cfg.mysqlPort(),
                cfg.mysqlDatabase(),
                cfg.mysqlUseSsl()
        ));
        config.setUsername(cfg.mysqlUsername());
        config.setPassword(cfg.mysqlPassword());
        config.setMaximumPoolSize(Math.max(1, cfg.mysqlPoolSize()));
        config.setConnectionTimeout(10_000L);
        return new HikariDataSource(config);
    }

    private void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id VARCHAR(64) PRIMARY KEY NOT NULL,
                    owner_id VARCHAR(36) NOT NULL,
                    owner_name VARCHAR(64) NOT NULL,
                    world VARCHAR(128) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    yaw FLOAT NOT NULL,
                    pitch FLOAT NOT NULL,
                    experience INT NOT NULL,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    items LONGTEXT NOT NULL,
                    marker_uuid VARCHAR(36),
                    clickbox_uuid VARCHAR(36),
                    hologram_uuids TEXT
                )
                """.formatted(TABLE);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create graves table", e);
            throw new IllegalStateException("Failed to create graves table", e);
        }
    }

    protected List<Grave> maybeImportYaml(List<Grave> existing) {
        if (!existing.isEmpty()) {
            return existing;
        }
        File yamlFile = new File(plugin.getDataFolder(), "graves.yml");
        if (!yamlFile.exists()) {
            return existing;
        }
        try {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(yamlFile);
            List<Grave> imported = new ArrayList<>();
            var root = data.getConfigurationSection("graves");
            if (root == null) {
                return existing;
            }
            for (String key : root.getKeys(false)) {
                var section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                try {
                    imported.add(Grave.load(section));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to import YAML grave " + key, e);
                }
            }
            if (imported.isEmpty()) {
                return existing;
            }
            writeRowsSync(GraveSnapshot.from(imported));
            plugin.getLogger().info("Imported " + imported.size() + " grave(s) from graves.yml into SQL storage.");
            return imported;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "YAML import into SQL storage failed", e);
            return existing;
        }
    }

    @Override
    public List<Grave> loadAll() {
        List<Grave> graves = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                try {
                    graves.add(fromResultSet(rs));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load grave row " + rs.getString("id"), e);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load graves from SQL", e);
        }
        return maybeImportYaml(graves);
    }

    @Override
    public void saveAll(Collection<Grave> graves) {
        List<GraveSnapshot> snapshot = GraveSnapshot.from(graves);
        SchedulerUtils.runAsync(plugin, () -> {
            synchronized (saveLock) {
                writeRowsSync(snapshot);
            }
        });
    }

    @Override
    public void saveAllSync(Collection<Grave> graves) {
        List<GraveSnapshot> snapshot = GraveSnapshot.from(graves);
        synchronized (saveLock) {
            writeRowsSync(snapshot);
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private void writeRowsSync(List<GraveSnapshot> snapshots) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Set<String> keep = new HashSet<>();
                try (PreparedStatement upsert = connection.prepareStatement(upsertSql())) {
                    for (GraveSnapshot row : snapshots) {
                        keep.add(row.id);
                        bindUpsert(upsert, row);
                        upsert.addBatch();
                    }
                    upsert.executeBatch();
                }

                try (PreparedStatement select = connection.prepareStatement("SELECT id FROM " + TABLE);
                     ResultSet rs = select.executeQuery()) {
                    List<String> remove = new ArrayList<>();
                    while (rs.next()) {
                        String id = rs.getString(1);
                        if (!keep.contains(id)) {
                            remove.add(id);
                        }
                    }
                    if (!remove.isEmpty()) {
                        try (PreparedStatement delete = connection.prepareStatement(
                                "DELETE FROM " + TABLE + " WHERE id = ?")) {
                            for (String id : remove) {
                                delete.setString(1, id);
                                delete.addBatch();
                            }
                            delete.executeBatch();
                        }
                    }
                }

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save graves to SQL", e);
        }
    }

    private void bindUpsert(PreparedStatement statement, GraveSnapshot row) throws SQLException {
        statement.setString(1, row.id);
        statement.setString(2, row.ownerId);
        statement.setString(3, row.ownerName);
        statement.setString(4, row.world == null ? "" : row.world);
        statement.setDouble(5, row.x);
        statement.setDouble(6, row.y);
        statement.setDouble(7, row.z);
        statement.setFloat(8, row.yaw);
        statement.setFloat(9, row.pitch);
        statement.setInt(10, row.experience);
        statement.setLong(11, row.createdAt);
        statement.setLong(12, row.expiresAt);
        statement.setString(13, row.items == null ? "" : row.items);
        statement.setString(14, row.markerUuid);
        statement.setString(15, row.clickBoxUuid);
        statement.setString(16, row.hologramUuids);
    }

    private Grave fromResultSet(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        UUID ownerId = UUID.fromString(rs.getString("owner_id"));
        String ownerName = rs.getString("owner_name");
        String worldName = rs.getString("world");
        double x = rs.getDouble("x");
        double y = rs.getDouble("y");
        double z = rs.getDouble("z");
        float yaw = rs.getFloat("yaw");
        float pitch = rs.getFloat("pitch");
        int experience = rs.getInt("experience");
        long createdAt = rs.getLong("created_at");
        long expiresAt = rs.getLong("expires_at");
        org.bukkit.inventory.ItemStack[] items = ItemSerializer.deserialize(rs.getString("items"));

        Location location = null;
        World world = worldName == null || worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        if (world != null) {
            location = new Location(world, x, y, z, yaw, pitch);
        }

        Grave grave = new Grave(id, ownerId, ownerName, location, items, experience, createdAt, expiresAt);
        if (location == null) {
            grave.setLocationCoords(worldName, x, y, z, yaw, pitch);
        }

        String marker = rs.getString("marker_uuid");
        if (marker != null && !marker.isBlank()) {
            grave.setMarkerUuid(UUID.fromString(marker));
        }
        String clickBox = rs.getString("clickbox_uuid");
        if (clickBox != null && !clickBox.isBlank()) {
            try {
                grave.setClickBoxUuid(UUID.fromString(clickBox));
            } catch (IllegalArgumentException ignored) {
            }
        }
        String hologramsRaw = rs.getString("hologram_uuids");
        List<UUID> holograms = new ArrayList<>();
        if (hologramsRaw != null && !hologramsRaw.isBlank()) {
            for (String part : hologramsRaw.split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    holograms.add(UUID.fromString(trimmed));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        grave.setHologramUuids(holograms);
        return grave;
    }
}
