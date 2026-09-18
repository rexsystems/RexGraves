package me.rexsystems.rexGraves.convert;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import me.rexsystems.rexGraves.RexGraves;
import me.rexsystems.rexGraves.grave.Grave;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Imports graves from AxGraves {@code data.json} into RexGraves.
 * Ax format: location = world;x;y;z;yaw;pitch, items = Base64(AxAPI item array bytes).
 */
public final class AxGravesConverter {

    private static final Gson GSON = new GsonBuilder().create();

    private final RexGraves plugin;

    public AxGravesConverter(RexGraves plugin) {
        this.plugin = plugin;
    }

    public static File resolveDefaultFile(RexGraves plugin) {
        File axData = new File(plugin.getDataFolder().getParentFile(), "AxGraves" + File.separator + "data.json");
        if (axData.isFile()) {
            return axData;
        }
        File localImport = new File(plugin.getDataFolder(), "import" + File.separator + "axgraves-data.json");
        if (localImport.isFile()) {
            return localImport;
        }
        File local = new File(plugin.getDataFolder(), "axgraves-data.json");
        if (local.isFile()) {
            return local;
        }
        return axData;
    }

    public Result convert(File file) {
        if (file == null || !file.isFile()) {
            return Result.missing(file);
        }

        SavedGrave[] saved;
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            saved = GSON.fromJson(reader, SavedGrave[].class);
        } catch (IOException | JsonSyntaxException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read AxGraves data from " + file.getAbsolutePath(), e);
            return Result.failed(file, e.getMessage());
        }

        if (saved == null || saved.length == 0) {
            return Result.empty(file);
        }

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < saved.length; i++) {
            SavedGrave entry = saved[i];
            try {
                Grave grave = convertOne(entry);
                if (grave == null) {
                    skipped++;
                    continue;
                }
                imported++;
            } catch (Exception e) {
                skipped++;
                String msg = "Entry #" + (i + 1) + ": " + e.getMessage();
                errors.add(msg);
                plugin.getLogger().log(Level.WARNING, "AxGraves convert skipped entry #" + (i + 1), e);
            }
        }

        return Result.ok(file, imported, skipped, errors);
    }

    private Grave convertOne(SavedGrave entry) {
        if (entry == null || entry.owner == null || entry.location == null) {
            throw new IllegalArgumentException("Missing owner or location");
        }

        UUID ownerId = UUID.fromString(entry.owner);
        OfflinePlayer offline = Bukkit.getOfflinePlayer(ownerId);
        String ownerName = offline.getName() == null ? "Unknown" : offline.getName();

        Location location = parseLocation(entry.location);
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("World not loaded for location: " + entry.location);
        }

        ItemStack[] items = deserializeItems(entry.items);
        long createdAt = entry.date > 0L ? entry.date : System.currentTimeMillis();
        int xp = Math.max(0, entry.xp);

        if (isEmpty(items) && xp <= 0) {
            return null;
        }

        return plugin.getGraveManager().importGrave(ownerId, ownerName, location, items, xp, createdAt);
    }

    private static Location parseLocation(String raw) {
        String[] split = raw.split(";");
        if (split.length < 4) {
            throw new IllegalArgumentException("Invalid AxGraves location: " + raw);
        }
        World world = Bukkit.getWorld(split[0]);
        if (world == null) {
            throw new IllegalArgumentException("World not loaded: " + split[0]);
        }
        float yaw = split.length > 4 ? Float.parseFloat(split[4]) : 0f;
        float pitch = split.length > 5 ? Float.parseFloat(split[5]) : 0f;
        return new Location(world, Double.parseDouble(split[1]), Double.parseDouble(split[2]), Double.parseDouble(split[3]), yaw, pitch);
    }

    private ItemStack[] deserializeItems(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return new ItemStack[0];
        }
        // Gson may leave unicode escapes already decoded; strip whitespace/newlines just in case.
        String cleaned = encoded.replace("\\u003d", "=").replace("\\u003D", "=").trim();
        byte[] payload = Base64.getDecoder().decode(cleaned);
        ItemStack[] items = AxItemArrayCodec.decode(payload);
        int expected = AxItemArrayCodec.countNonEmptySlots(payload);
        int got = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                got++;
            }
        }
        if (expected > 0 && got == 0) {
            throw new IllegalStateException("Decoded 0/" + expected + " items from AxGraves payload");
        }
        if (expected > got) {
            plugin.getLogger().warning("AxGraves convert: only restored " + got + "/" + expected + " item stacks");
        }
        return items;
    }

    private static boolean isEmpty(ItemStack[] items) {
        if (items == null) {
            return true;
        }
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gson DTO matching AxGraves SavedGrave fields.
     */
    @SuppressWarnings("unused")
    private static final class SavedGrave {
        String location;
        String owner;
        String texture;
        String items;
        int xp;
        long date;
    }

    public record Result(File file, boolean success, int imported, int skipped, String error, List<String> details) {
        static Result missing(File file) {
            return new Result(file, false, 0, 0, "File not found: " + (file == null ? "?" : file.getAbsolutePath()), List.of());
        }

        static Result failed(File file, String error) {
            return new Result(file, false, 0, 0, error == null ? "Read failed" : error, List.of());
        }

        static Result empty(File file) {
            return new Result(file, true, 0, 0, null, List.of());
        }

        static Result ok(File file, int imported, int skipped, List<String> details) {
            return new Result(file, true, imported, skipped, null, details == null ? List.of() : details);
        }
    }

    /**
     * Decodes AxAPI ItemArraySerializer bytes (int count, then short-len + item bytes).
     * Item bytes are Ax/Paper compressed NBT with DataVersion.
     */
    static final class AxItemArrayCodec {
        private static final Method DESERIALIZE_BYTES;
        private static final Method UNSAFE_DESERIALIZE_ITEM;

        static {
            Method deserializeBytes = null;
            Method unsafeDeserialize = null;
            try {
                deserializeBytes = ItemStack.class.getMethod("deserializeBytes", byte[].class);
            } catch (NoSuchMethodException ignored) {
            }
            try {
                unsafeDeserialize = Class.forName("org.bukkit.UnsafeValues")
                        .getMethod("deserializeItem", byte[].class);
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            }
            DESERIALIZE_BYTES = deserializeBytes;
            UNSAFE_DESERIALIZE_ITEM = unsafeDeserialize;
        }

        private AxItemArrayCodec() {
        }

        static int countNonEmptySlots(byte[] value) {
            try {
                java.io.DataInputStream input = new java.io.DataInputStream(new java.io.ByteArrayInputStream(value));
                int length = input.readInt();
                int counted = 0;
                for (int i = 0; i < length; i++) {
                    int size = input.readUnsignedShort();
                    if (size > 0) {
                        counted++;
                        input.skipBytes(size);
                    }
                }
                return counted;
            } catch (IOException e) {
                return -1;
            }
        }

        static ItemStack[] decode(byte[] value) {
            java.io.DataInputStream input = new java.io.DataInputStream(new java.io.ByteArrayInputStream(value));
            try {
                int length = input.readInt();
                if (length < 0 || length > 10_000) {
                    throw new IllegalStateException("Invalid AxGraves item count: " + length);
                }
                ItemStack[] items = new ItemStack[length];
                for (int i = 0; i < length; i++) {
                    int size = input.readUnsignedShort();
                    if (size == 0) {
                        items[i] = null;
                        continue;
                    }
                    byte[] read = new byte[size];
                    input.readFully(read);
                    try {
                        items[i] = deserializeItem(read);
                    } catch (Throwable t) {
                        // Soft-skip broken items instead of nuking the whole grave
                        items[i] = null;
                    }
                }
                return items;
            } catch (IOException e) {
                throw new IllegalStateException("Invalid AxGraves item payload", e);
            }
        }

        private static ItemStack deserializeItem(byte[] bytes) {
            Throwable last = null;

            if (DESERIALIZE_BYTES != null) {
                try {
                    ItemStack stack = (ItemStack) DESERIALIZE_BYTES.invoke(null, (Object) bytes);
                    if (stack == null || stack.getType() == Material.AIR || stack.getType().isAir()) {
                        return null;
                    }
                    return stack;
                } catch (Throwable t) {
                    last = unwrap(t);
                }
            }

            if (UNSAFE_DESERIALIZE_ITEM != null) {
                try {
                    ItemStack stack = (ItemStack) UNSAFE_DESERIALIZE_ITEM.invoke(Bukkit.getUnsafe(), (Object) bytes);
                    if (stack == null || stack.getType() == Material.AIR || stack.getType().isAir()) {
                        return null;
                    }
                    return stack;
                } catch (Throwable t) {
                    last = unwrap(t);
                }
            }

            if (last != null) {
                throw new IllegalStateException("Failed to deserialize AxGraves item", last);
            }
            throw new IllegalStateException("No compatible Paper item deserialize API on this server");
        }

        private static Throwable unwrap(Throwable t) {
            if (t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null) {
                return ite.getCause();
            }
            return t;
        }
    }
}
