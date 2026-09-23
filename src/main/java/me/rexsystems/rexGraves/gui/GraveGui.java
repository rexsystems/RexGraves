package me.rexsystems.rexGraves.gui;

import me.rexsystems.rexGraves.RexGraves;
import me.rexsystems.rexGraves.grave.Grave;
import me.rexsystems.rexGraves.util.MessageService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Grave loot GUI mirroring the stored inventory slot layout.
 */
public class GraveGui implements InventoryHolder {

    private final RexGraves plugin;
    private final Grave grave;
    private final Inventory inventory;
    private final AtomicBoolean syncPending = new AtomicBoolean();

    public GraveGui(RexGraves plugin, Grave grave) {
        this.plugin = plugin;
        this.grave = grave;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", grave.getOwnerName());
        placeholders.put("owner", grave.getOwnerName());
        Component title = MessageService.parse(plugin.getConfigManager().guiTitle(), placeholders);

        int needed = Math.max(grave.getItems().length, 9);
        int size = Math.max(plugin.getConfigManager().guiSize(), ((needed + 8) / 9) * 9);
        size = Math.min(54, Math.max(9, size));
        this.inventory = Bukkit.createInventory(this, size, title);
        fill();
    }

    private void fill() {
        inventory.clear();
        ItemStack[] items = grave.getItems();
        int size = inventory.getSize();
        for (int i = 0; i < items.length && i < size; i++) {
            ItemStack item = items[i];
            if (item != null && !item.getType().isAir()) {
                inventory.setItem(i, item.clone());
            }
        }
    }

    public Grave getGrave() {
        return grave;
    }

    /** @return true if the caller should schedule a sync (none pending yet) */
    public boolean markSyncPending() {
        return syncPending.compareAndSet(false, true);
    }

    public void clearSyncPending() {
        syncPending.set(false);
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void syncFromInventory() {
        ItemStack[] original = grave.getItems();
        int length = Math.max(original.length, inventory.getSize());
        ItemStack[] synced = new ItemStack[length];

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            synced[i] = item == null || item.getType().isAir() ? null : item.clone();
        }
        for (int i = inventory.getSize(); i < original.length; i++) {
            synced[i] = original[i] == null ? null : original[i].clone();
        }
        grave.setItems(synced);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
