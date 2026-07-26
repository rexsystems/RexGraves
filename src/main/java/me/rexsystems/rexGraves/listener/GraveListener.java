package me.rexsystems.rexGraves.listener;

import me.rexsystems.rexGraves.RexGraves;
import me.rexsystems.rexGraves.grave.Grave;
import me.rexsystems.rexGraves.gui.GraveGui;
import me.rexsystems.rexGraves.util.GraveKeys;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
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
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

public class GraveListener implements Listener {

    private final RexGraves plugin;

    public GraveListener(RexGraves plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        handleInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerInteractAtEntityEvent) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        handleInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handleInteract(Player player, Entity entity, org.bukkit.event.Cancellable event) {
        Optional<Grave> graveOpt = resolveGrave(entity);
        if (graveOpt.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        plugin.getGraveManager().openGrave(player, graveOpt.get(), player.isSneaking());
    }

    private Optional<Grave> resolveGrave(Entity entity) {
        if (entity instanceof ArmorStand || entity instanceof Interaction) {
            Optional<Grave> byMarker = plugin.getGraveManager().byMarker(entity.getUniqueId());
            if (byMarker.isPresent()) {
                return byMarker;
            }
            String tagged = GraveKeys.readGraveId(entity.getPersistentDataContainer(), plugin);
            if (tagged != null) {
                return plugin.getGraveManager().get(tagged);
            }
        }
        return Optional.empty();
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
        Entity damaged = event.getEntity();
        if (!(damaged instanceof ArmorStand) && !(damaged instanceof Interaction)) {
            return;
        }
        Optional<Grave> graveOpt = resolveGrave(damaged);
        if (graveOpt.isEmpty()) {
            return;
        }
        event.setCancelled(true);

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player breaker = resolvePlayer(byEntity.getDamager());
            if (breaker != null && breaker.hasPermission("rexgraves.break")) {
                boolean drop = breaker.isSneaking();
                plugin.getGraveManager().removeGrave(graveOpt.get(), true, drop);
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
