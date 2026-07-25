package me.rexsystems.rexGraves.listener;

import me.rexsystems.rexGraves.RexGraves;
import me.rexsystems.rexGraves.grave.Grave;
import me.rexsystems.rexGraves.gui.GraveGui;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Optional;

public class GraveListener implements Listener {

    private final RexGraves plugin;

    public GraveListener(RexGraves plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractAtEntityEvent event) {
        handleInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntityLegacy(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerInteractAtEntityEvent) {
            return;
        }
        handleInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handleInteract(Player player, Entity entity, org.bukkit.event.Cancellable event) {
        if (!(entity instanceof ArmorStand)) {
            return;
        }
        Optional<Grave> graveOpt = plugin.getGraveManager().byMarker(entity.getUniqueId());
        if (graveOpt.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        plugin.getGraveManager().openGrave(player, graveOpt.get());
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Optional<Grave> graveOpt = plugin.getGraveManager().byMarker(event.getRightClicked().getUniqueId());
        if (graveOpt.isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) {
            return;
        }
        Optional<Grave> graveOpt = plugin.getGraveManager().byMarker(stand.getUniqueId());
        if (graveOpt.isEmpty()) {
            return;
        }
        event.setCancelled(true);

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player breaker = resolvePlayer(byEntity.getDamager());
            if (breaker != null && breaker.hasPermission("rexgraves.break")) {
                Grave grave = graveOpt.get();
                boolean drop = breaker.isSneaking();
                plugin.getGraveManager().removeGrave(grave, true, drop);
            }
        }
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof GraveGui gui)) {
            return;
        }
        plugin.getGraveManager().handleGuiClose(player, gui);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.getGraveManager().handleChunkLoad(event.getChunk());
    }
}
