package me.rexsystems.rexGraves.grave;

import me.rexsystems.rexGraves.util.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Grave {

    private final String id;
    private final UUID ownerId;
    private final String ownerName;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private ItemStack[] items;
    private int experience;
    private final long createdAt;
    private final long expiresAt;
    private UUID markerUuid;
    private UUID clickBoxUuid;
    private final List<UUID> hologramUuids = new ArrayList<>();

    public Grave(
            String id,
            UUID ownerId,
            String ownerName,
            Location location,
            ItemStack[] items,
            int experience,
            long createdAt,
            long expiresAt
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        setLocation(location);
        this.items = items == null ? new ItemStack[0] : items;
        this.experience = Math.max(0, experience);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    public void setLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        this.worldName = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }

    public void setLocationCoords(String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public ItemStack[] getItems() {
        return items;
    }

    public void setItems(ItemStack[] items) {
        this.items = items == null ? new ItemStack[0] : items;
    }

    public int getExperience() {
        return experience;
    }

    public void setExperience(int experience) {
        this.experience = Math.max(0, experience);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean neverExpires() {
        return expiresAt <= 0L;
    }

    public boolean isExpired(long now) {
        return !neverExpires() && now >= expiresAt;
    }

    public long remainingMillis(long now) {
        if (neverExpires()) {
            return -1L;
        }
        return Math.max(0L, expiresAt - now);
    }

    public int countItems() {
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public boolean isEmpty() {
        if (experience > 0) {
            return false;
        }
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                return false;
            }
        }
        return true;
    }

    public UUID getMarkerUuid() {
        return markerUuid;
    }

    public void setMarkerUuid(UUID markerUuid) {
        this.markerUuid = markerUuid;
    }

    public UUID getClickBoxUuid() {
        return clickBoxUuid;
    }

    public void setClickBoxUuid(UUID clickBoxUuid) {
        this.clickBoxUuid = clickBoxUuid;
    }

    public List<UUID> getHologramUuids() {
        return hologramUuids;
    }

    public void setHologramUuids(List<UUID> uuids) {
        hologramUuids.clear();
        if (uuids != null) {
            hologramUuids.addAll(uuids);
        }
    }

    public void save(ConfigurationSection section) {
        section.set("id", id);
        section.set("owner-id", ownerId.toString());
        section.set("owner-name", ownerName);
        section.set("world", worldName);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("yaw", yaw);
        section.set("pitch", pitch);
        section.set("experience", experience);
        section.set("created-at", createdAt);
        section.set("expires-at", expiresAt);
        section.set("items", ItemSerializer.serialize(items));
        section.set("marker-uuid", markerUuid == null ? null : markerUuid.toString());
        section.set("clickbox-uuid", clickBoxUuid == null ? null : clickBoxUuid.toString());
        List<String> holograms = new ArrayList<>();
        for (UUID uuid : hologramUuids) {
            holograms.add(uuid.toString());
        }
        section.set("hologram-uuids", holograms);
    }

    public static Grave load(ConfigurationSection section) {
        String id = section.getString("id");
        UUID ownerId = UUID.fromString(section.getString("owner-id"));
        String ownerName = section.getString("owner-name", "Unknown");
        String world = section.getString("world");
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");
        int experience = section.getInt("experience", 0);
        long createdAt = section.getLong("created-at", System.currentTimeMillis());
        long expiresAt = section.getLong("expires-at", 0L);
        ItemStack[] items = ItemSerializer.deserialize(section.getString("items", ""));

        Location location = null;
        World bukkitWorld = world == null ? null : Bukkit.getWorld(world);
        if (bukkitWorld != null) {
            location = new Location(bukkitWorld, x, y, z, yaw, pitch);
        }

        Grave grave = new Grave(id, ownerId, ownerName, location, items, experience, createdAt, expiresAt);
        if (location == null) {
            grave.worldName = world;
            grave.x = x;
            grave.y = y;
            grave.z = z;
            grave.yaw = yaw;
            grave.pitch = pitch;
        }

        String marker = section.getString("marker-uuid");
        if (marker != null && !marker.isBlank()) {
            grave.setMarkerUuid(UUID.fromString(marker));
        }
        String clickBox = section.getString("clickbox-uuid");
        if (clickBox != null && !clickBox.isBlank()) {
            try {
                grave.setClickBoxUuid(UUID.fromString(clickBox));
            } catch (IllegalArgumentException ignored) {
            }
        }
        List<String> holograms = section.getStringList("hologram-uuids");
        List<UUID> hologramUuids = new ArrayList<>();
        for (String raw : holograms) {
            try {
                hologramUuids.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
        grave.setHologramUuids(hologramUuids);
        return grave;
    }
}
