package me.rexsystems.rexGraves.util;

import me.rexsystems.rexGraves.RexGraves;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class GraveKeys {

    private GraveKeys() {
    }

    public static NamespacedKey graveId(RexGraves plugin) {
        return new NamespacedKey(plugin, "grave_id");
    }

    public static NamespacedKey hologram(RexGraves plugin) {
        return new NamespacedKey(plugin, "grave_hologram");
    }

    public static void tagGrave(PersistentDataContainer container, RexGraves plugin, String graveId) {
        container.set(graveId(plugin), PersistentDataType.STRING, graveId);
    }

    public static void tagHologram(PersistentDataContainer container, RexGraves plugin, String graveId) {
        container.set(hologram(plugin), PersistentDataType.STRING, graveId);
        container.set(graveId(plugin), PersistentDataType.STRING, graveId);
    }

    public static String readGraveId(PersistentDataContainer container, RexGraves plugin) {
        return container.get(graveId(plugin), PersistentDataType.STRING);
    }
}
