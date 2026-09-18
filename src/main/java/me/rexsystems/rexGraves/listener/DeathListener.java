package me.rexsystems.rexGraves.listener;

import me.rexsystems.rexGraves.RexGraves;
import me.rexsystems.rexGraves.util.MessageService;
import org.bukkit.GameRule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class DeathListener implements Listener {

    private final RexGraves plugin;

    public DeathListener(RexGraves plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfigManager().isEnabled()) {
            return;
        }

        Player player = event.getEntity();
        if (!player.hasPermission("rexgraves.use")) {
            return;
        }

        if (player.getWorld() != null) {
            Boolean keepInventory = player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY);
            if (Boolean.TRUE.equals(keepInventory) || event.getKeepInventory()) {
                MessageService.send(player, plugin.getConfigManager().prefixed("keep-inventory"));
                return;
            }
        }

        if (!plugin.getConfigManager().isWorldAllowed(player.getWorld().getName())) {
            MessageService.send(player, plugin.getConfigManager().prefixed("disabled-world"));
            return;
        }

        if (player.getLastDamageCause() != null
                && plugin.getConfigManager().isDeathCauseBlacklisted(player.getLastDamageCause().getCause().name())) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        ItemStack[] snapshot = new ItemStack[contents.length];
        boolean hasItems = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && !item.getType().isAir()) {
                snapshot[i] = item.clone();
                hasItems = true;
            }
        }

        int xpToStore = 0;
        if (plugin.getConfigManager().storeExperience()) {
            int total = Math.max(0, event.getDroppedExp());
            int percent = plugin.getConfigManager().experiencePercent();
            xpToStore = (int) Math.floor(total * (percent / 100.0));
        }

        if (!hasItems && xpToStore <= 0) {
            MessageService.send(player, plugin.getConfigManager().prefixed("empty-death"));
            return;
        }

        // Keep exact slots: stop vanilla drops, then clear the inventory ourselves.
        event.setKeepInventory(true);
        event.getDrops().clear();
        if (plugin.getConfigManager().storeExperience()) {
            event.setDroppedExp(0);
        }
        inventory.clear();

        plugin.getGraveManager().createFromDeath(player, snapshot, xpToStore);
    }
}
