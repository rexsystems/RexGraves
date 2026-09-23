package me.rexsystems.rexGraves.storage;

import me.rexsystems.rexGraves.RexGraves;
import me.rexsystems.rexGraves.grave.Grave;
import me.rexsystems.rexGraves.util.SchedulerUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class YamlGraveRepository implements GraveRepository {

    private final RexGraves plugin;
    private final File file;
    private final Object saveLock = new Object();
    private final AtomicLong generations = new AtomicLong();
    private long writtenGeneration;

    public YamlGraveRepository(RexGraves plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "graves.yml");
    }

    public File getFile() {
        return file;
    }

    @Override
    public List<Grave> loadAll() {
        return loadAllLogged();
    }

    public static List<Grave> readFrom(FileConfiguration data) {
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
            } catch (Exception ignored) {
            }
        }
        return graves;
    }

    private List<Grave> loadAllLogged() {
        ensureFile();
        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
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

    @Override
    public void saveAll(Collection<Grave> graves, Runnable onFailure) {
        YamlConfiguration snapshot = buildSnapshot(graves);
        long generation = generations.incrementAndGet();
        SchedulerUtils.runAsync(plugin, () -> {
            if (!write(snapshot, generation)) {
                onFailure.run();
            }
        });
    }

    @Override
    public void saveAllSync(Collection<Grave> graves) {
        YamlConfiguration snapshot = buildSnapshot(graves);
        write(snapshot, generations.incrementAndGet());
    }

    /**
     * Async tasks can run out of order; never let an older snapshot overwrite a newer one.
     * @return false only if the write itself failed
     */
    private boolean write(YamlConfiguration snapshot, long generation) {
        synchronized (saveLock) {
            if (generation <= writtenGeneration) {
                return true;
            }
            if (!writeSnapshot(snapshot)) {
                return false;
            }
            writtenGeneration = generation;
            return true;
        }
    }

    @Override
    public void close() {
        // no-op
    }

    private void ensureFile() {
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
    }

    private YamlConfiguration buildSnapshot(Collection<Grave> graves) {
        YamlConfiguration snapshot = new YamlConfiguration();
        ConfigurationSection root = snapshot.createSection("graves");
        for (Grave grave : new ArrayList<>(graves)) {
            try {
                ConfigurationSection section = root.createSection(grave.getId());
                grave.save(section);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to serialize grave " + grave.getId(), e);
            }
        }
        return snapshot;
    }

    private boolean writeSnapshot(YamlConfiguration snapshot) {
        Path target = file.toPath();
        Path temp = target.resolveSibling(file.getName() + ".tmp");
        try {
            String yaml = snapshot.saveToString();
            try (FileOutputStream fos = new FileOutputStream(temp.toFile());
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                writer.write(yaml);
                writer.flush();
                fos.getFD().sync();
            }

            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save graves.yml", e);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
            return false;
        }
    }
}
