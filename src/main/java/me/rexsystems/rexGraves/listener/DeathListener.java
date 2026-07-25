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

import java.util.ArrayList;
import java.util.List;

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
                return;
            }
        }

        if (!plugin.getConfigManager().isWorldAllowed(player.getWorld().getName())) {
            return;
        }

        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            if (drop != null && !drop.getType().isAir()) {
                drops.add(drop.clone());
            }
        }

        int xpToStore = 0;
        if (plugin.getConfigManager().storeExperience()) {
            int total = Math.max(0, event.getDroppedExp());
            int percent = plugin.getConfigManager().experiencePercent();
            xpToStore = (int) Math.floor(total * (percent / 100.0));
        }

        if (drops.isEmpty() && xpToStore <= 0) {
            MessageService.send(player, plugin.getConfigManager().prefixed("empty-death"));
            return;
        }

        event.getDrops().clear();
        if (plugin.getConfigManager().storeExperience()) {
            event.setDroppedExp(0);
        }

        plugin.getGraveManager().createFromDeath(player, drops, xpToStore);
    }
}
