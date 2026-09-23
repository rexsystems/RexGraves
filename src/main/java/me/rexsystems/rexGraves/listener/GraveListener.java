package me.rexsystems.rexGraves.listener;

import me.rexsystems.rexGraves.RexGraves;
import me.rexsystems.rexGraves.grave.Grave;
import me.rexsystems.rexGraves.gui.GraveGui;
import me.rexsystems.rexGraves.util.GraveKeys;
import me.rexsystems.rexGraves.util.MessageService;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class GraveListener implements Listener {

    private final RexGraves plugin;

    /** Tracks pending break confirmations: player UUID -> PendingBreak */
    private final Map<UUID, PendingBreak> pendingBreaks = new HashMap<>();

    private static final long CONFIRM_TIMEOUT_MS = 5000L;

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
        if (resolveGrave(event.getRightClicked()).isPresent()) {
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

        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean explosion = cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
        if (explosion) {
            if (plugin.getConfigManager().protectFromExplosions()) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            plugin.getGraveManager().removeGrave(graveOpt.get(), true, true);
            return;
        }

        event.setCancelled(true);

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player breaker = resolvePlayer(byEntity.getDamager());
            if (breaker != null && breaker.hasPermission("rexgraves.break")) {
                handleBreakAttempt(breaker, graveOpt.get());
            }
        }
    }

    private void handleBreakAttempt(Player breaker, Grave grave) {
        boolean drop = breaker.isSneaking();
        long now = System.currentTimeMillis();
        UUID playerId = breaker.getUniqueId();

        PendingBreak pending = pendingBreaks.get(playerId);
        if (pending != null && pending.graveId.equals(grave.getId())
                && pending.drop == drop
                && (now - pending.timestamp) < CONFIRM_TIMEOUT_MS) {
            // Confirmed! Execute the break
            pendingBreaks.remove(playerId);
            plugin.getGraveManager().removeGrave(grave, true, drop);
            return;
        }

        // First hit — ask for confirmation
        pendingBreaks.put(playerId, new PendingBreak(grave.getId(), drop, now));

        Map<String, String> placeholders = plugin.getGraveManager().placeholders(grave, 1);
        if (drop) {
            MessageService.send(breaker, plugin.getConfigManager().prefixed("break-confirm-drop"), placeholders);
        } else {
            MessageService.send(breaker, plugin.getConfigManager().prefixed("break-confirm-destroy"), placeholders);
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && event.getView().getTopInventory().getHolder() instanceof GraveGui gui) {
            plugin.getGraveManager().scheduleGuiSync(player, gui);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && event.getView().getTopInventory().getHolder() instanceof GraveGui gui) {
            plugin.getGraveManager().scheduleGuiSync(player, gui);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plugin.getGraveManager().handleEntitiesLoad(event.getChunk(), event.getEntities());
    }

    private record PendingBreak(String graveId, boolean drop, long timestamp) {
    }
}
