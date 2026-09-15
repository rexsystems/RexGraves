package me.rexsystems.rexGraves.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public final class ItemSerializer {

    private ItemSerializer() {
    }

    public static String serialize(ItemStack[] items) {
        if (items == null) {
            return "";
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             BukkitObjectOutputStream data = new BukkitObjectOutputStream(output)) {
            data.writeInt(items.length);
            for (ItemStack item : items) {
                data.writeObject(item);
            }
            data.flush();
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize items", e);
        }
    }

    public static ItemStack[] deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ItemStack[0];
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(Base64.getDecoder().decode(raw));
             BukkitObjectInputStream data = new BukkitObjectInputStream(input)) {
            int size = data.readInt();
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) data.readObject();
            }
            return items;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize items", e);
        }
    }
}
