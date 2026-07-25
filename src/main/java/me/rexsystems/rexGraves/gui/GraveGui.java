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

public class GraveGui implements InventoryHolder {

    private final RexGraves plugin;
    private final Grave grave;
    private final Inventory inventory;

    public GraveGui(RexGraves plugin, Grave grave) {
        this.plugin = plugin;
        this.grave = grave;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", grave.getOwnerName());
        placeholders.put("owner", grave.getOwnerName());
        Component title = MessageService.parse(plugin.getConfigManager().guiTitle(), placeholders);
        this.inventory = Bukkit.createInventory(this, plugin.getConfigManager().guiSize(), title);
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

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void syncFromInventory() {
        ItemStack[] contents = inventory.getContents();
        ItemStack[] compact = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            compact[i] = item == null || item.getType().isAir() ? null : item.clone();
        }
        grave.setItems(compact);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
