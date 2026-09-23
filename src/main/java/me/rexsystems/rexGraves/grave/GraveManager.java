package me.rexsystems.rexGraves.grave;

import me.rexsystems.rexGraves.RexGraves;
import me.rexsystems.rexGraves.gui.GraveGui;
import me.rexsystems.rexGraves.storage.GraveRepository;
import me.rexsystems.rexGraves.storage.GraveRepositoryFactory;
import me.rexsystems.rexGraves.util.GraveKeys;
import me.rexsystems.rexGraves.util.LocationUtil;
import me.rexsystems.rexGraves.util.MessageService;
import me.rexsystems.rexGraves.util.ExperienceUtils;
import me.rexsystems.rexGraves.util.SchedulerUtils;
import me.rexsystems.rexGraves.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class GraveManager {

    private final RexGraves plugin;
    private final GraveRepository storage;
    private final GraveHologram hologram;
    private final Map<String, Grave> graves = new ConcurrentHashMap<>();
    private final Map<UUID, String> markerIndex = new ConcurrentHashMap<>();
    private final java.util.Set<String> lockedGraves = ConcurrentHashMap.newKeySet();
    private static final double VIEW_RANGE = 64.0;

    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean saveQueued = new AtomicBoolean();

    public GraveManager(RexGraves plugin) {
        this.plugin = plugin;
        this.storage = GraveRepositoryFactory.create(plugin);
        this.hologram = new GraveHologram(plugin);
    }

    public void load() {
        graves.clear();
        markerIndex.clear();
        for (Grave grave : storage.loadAll()) {
            graves.put(grave.getId(), grave);
            if (grave.getMarkerUuid() != null) {
                markerIndex.put(grave.getMarkerUuid(), grave.getId());
            }
            if (grave.getClickBoxUuid() != null) {
                markerIndex.put(grave.getClickBoxUuid(), grave.getId());
            }
        }
        plugin.getLogger().info("Loaded " + graves.size() + " graves.");

        for (Grave grave : graves.values()) {
            Location location = grave.getLocation();
            if (location == null || location.getWorld() == null) {
                continue;
            }
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            SchedulerUtils.runAtLocation(plugin, location, () -> spawnVisuals(grave, false));
        }
    }

    /**
     * Mark data dirty and write once on the next tick. Many saves in one tick (startup respawns,
     * bulk imports, GUI clicks) collapse into a single snapshot + async write.
     */
    public void save() {
        dirty.set(true);
        if (!plugin.isEnabled() || !saveQueued.compareAndSet(false, true)) {
            return;
        }
        SchedulerUtils.runLater(plugin, () -> {
            saveQueued.set(false);
            flush();
        }, 1L);
    }

    /** Autosave entry point: only writes if something changed since the last write. */
    public void saveNow() {
        flush();
    }

    private void flush() {
        if (dirty.compareAndSet(true, false)) {
            // Failed writes re-mark dirty so the next autosave retries instead of skipping.
            storage.saveAll(graves.values(), () -> dirty.set(true));
        }
    }

    public Collection<Grave> getAll() {
        return graves.values();
    }

    public Optional<Grave> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(graves.get(id));
    }

    /** Fast index lookup only; callers holding the entity should fall back to its PDC tag. */
    public Optional<Grave> byMarker(UUID markerUuid) {
        return get(markerIndex.get(markerUuid));
    }

    public List<Grave> getByOwner(UUID ownerId) {
        List<Grave> list = new ArrayList<>();
        for (Grave grave : graves.values()) {
            if (grave.getOwnerId().equals(ownerId)) {
                list.add(grave);
            }
        }
        list.sort(Comparator.comparingLong(Grave::getCreatedAt));
        return list;
    }

    public List<Grave> getAllSorted() {
        List<Grave> list = new ArrayList<>(graves.values());
        list.sort(Comparator
                .comparing(Grave::getOwnerName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(Grave::getCreatedAt));
        return list;
    }

    public Grave createFromDeath(Player player, ItemStack[] snapshot, int experienceToStore) {
        Location safe = LocationUtil.graveLocation(player.getLocation());
        enforceMaxGraves(player.getUniqueId());

        long now = System.currentTimeMillis();
        long expirationSeconds = plugin.getConfigManager().expirationSeconds();
        long expiresAt = expirationSeconds <= 0 ? 0L : now + (expirationSeconds * 1000L);

        ItemStack[] items = snapshot == null ? new ItemStack[0] : snapshot;
        String id = UUID.randomUUID().toString().substring(0, 8);
        while (graves.containsKey(id)) {
            id = UUID.randomUUID().toString().substring(0, 8);
        }
        Grave grave = new Grave(
                id,
                player.getUniqueId(),
                player.getName(),
                safe,
                items,
                experienceToStore,
                now,
                expiresAt
        );

        graves.put(id, grave);
        SchedulerUtils.runAtLocation(plugin, safe, () -> {
            spawnVisuals(grave, true);
            playCreateEffects(safe);
        });

        Map<String, String> placeholders = placeholders(grave, 1);
        MessageService.send(player, plugin.getConfigManager().prefixed("created"), placeholders);
        if (experienceToStore > 0) {
            MessageService.send(player, plugin.getConfigManager().prefixed("created-xp"), placeholders);
        }

        save();
        return grave;
    }

    /**
     * Import an existing grave (e.g. AxGraves conversion). Spawns visuals and persists.
     */
    public Grave importGrave(UUID ownerId, String ownerName, Location location, ItemStack[] items, int experience, long createdAt) {
        Location safe = location == null ? null : LocationUtil.graveLocation(location);
        if (safe == null || safe.getWorld() == null) {
            throw new IllegalArgumentException("Invalid grave location");
        }

        long now = System.currentTimeMillis();
        long created = createdAt > 0L ? createdAt : now;
        // Fresh expiry window from import time. Old Ax dates + short TTL would wipe graves instantly.
        long expirationSeconds = plugin.getConfigManager().expirationSeconds();
        long expiresAt = expirationSeconds <= 0 ? 0L : now + (expirationSeconds * 1000L);

        String id = UUID.randomUUID().toString().substring(0, 8);
        while (graves.containsKey(id)) {
            id = UUID.randomUUID().toString().substring(0, 8);
        }

        String name = ownerName == null || ownerName.isBlank() ? "Unknown" : ownerName;
        Grave grave = new Grave(
                id,
                ownerId,
                name,
                safe,
                items == null ? new ItemStack[0] : items,
                Math.max(0, experience),
                created,
                expiresAt
        );

        graves.put(id, grave);
        // Unloaded chunks get their visuals from handleEntitiesLoad; spawning now would force-load them.
        if (safe.getWorld().isChunkLoaded(safe.getBlockX() >> 4, safe.getBlockZ() >> 4)) {
            SchedulerUtils.runAtLocation(plugin, safe, () -> spawnVisuals(grave, true));
        }
        save();
        return grave;
    }

    private void enforceMaxGraves(UUID ownerId) {
        int max = plugin.getConfigManager().maxGravesPerPlayer();
        if (max <= 0) {
            return;
        }
        List<Grave> owned = getByOwner(ownerId);
        while (owned.size() >= max) {
            if (!plugin.getConfigManager().removeOldestWhenFull()) {
                break;
            }
            Grave oldest = owned.get(0);
            removeGrave(oldest, false, false);
            owned.remove(0);
        }
    }

    public void spawnVisuals(Grave grave, boolean fresh) {
        Location location = grave.getLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }

        clearNearbyTagged(location, grave.getId());
        removeVisuals(grave, false);

        // Visual marker. marker=false so it keeps a hitbox as a click fallback.
        ArmorStand marker = location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(false);
            stand.setSmall(plugin.getConfigManager().smallMarker());
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setInvulnerable(true);
            stand.setPersistent(true);
            stand.setSilent(true);
            stand.setCollidable(false);
            stand.setCustomNameVisible(false);
            GraveKeys.tagGrave(stand.getPersistentDataContainer(), plugin, grave.getId());
            try {
                stand.setCanTick(false);
            } catch (NoSuchMethodError ignored) {
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                try {
                    stand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                    stand.addEquipmentLock(slot, ArmorStand.LockType.ADDING);
                } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
                }
            }

            if (plugin.getConfigManager().playerHead()) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(grave.getOwnerId()));
                    head.setItemMeta(meta);
                }
                stand.getEquipment().setHelmet(head);
            } else {
                stand.getEquipment().setHelmet(new ItemStack(Material.SKELETON_SKULL));
            }
        });

        // Dedicated click hitbox (ArmorStand marker=true has no hitbox).
        Interaction clickBox = location.getWorld().spawn(location.clone().add(0, 0.1, 0), Interaction.class, interaction -> {
            interaction.setInteractionWidth(1.0f);
            interaction.setInteractionHeight(1.5f);
            interaction.setResponsive(true);
            interaction.setPersistent(true);
            interaction.setInvulnerable(true);
            GraveKeys.tagGrave(interaction.getPersistentDataContainer(), plugin, grave.getId());
        });

        grave.setMarkerUuid(marker.getUniqueId());
        grave.setClickBoxUuid(clickBox.getUniqueId());
        markerIndex.put(marker.getUniqueId(), grave.getId());
        markerIndex.put(clickBox.getUniqueId(), grave.getId());

        List<UUID> holograms = hologram.spawn(grave);
        grave.setHologramUuids(holograms);

        save();
    }

    private void clearNearbyTagged(Location location, String graveId) {
        if (location.getWorld() == null) {
            return;
        }
        for (Entity entity : location.getWorld().getNearbyEntities(location, 2.5, 4.0, 2.5)) {
            String tagged = GraveKeys.readGraveId(entity.getPersistentDataContainer(), plugin);
            if (tagged == null) {
                continue;
            }
            if (tagged.equals(graveId) || !graves.containsKey(tagged)) {
                if (entity instanceof ArmorStand || entity instanceof TextDisplay || entity instanceof Interaction) {
                    markerIndex.remove(entity.getUniqueId());
                    entity.remove();
                }
            }
        }
    }

    public void removeVisuals(Grave grave, boolean clearIndex) {
        Location location = grave.getLocation();
        if (location != null) {
            clearNearbyTagged(location, grave.getId());
        }
        hologram.remove(grave);
        UUID markerUuid = grave.getMarkerUuid();
        if (markerUuid != null) {
            Entity entity = findEntity(grave, markerUuid);
            if (entity != null) {
                entity.remove();
            }
            if (clearIndex) {
                markerIndex.remove(markerUuid);
            }
            grave.setMarkerUuid(null);
        }
        UUID clickBoxUuid = grave.getClickBoxUuid();
        if (clickBoxUuid != null) {
            Entity entity = findEntity(grave, clickBoxUuid);
            if (entity != null) {
                entity.remove();
            }
            if (clearIndex) {
                markerIndex.remove(clickBoxUuid);
            }
            grave.setClickBoxUuid(null);
        }
    }

    /**
     * @param takeAll true for shift+right-click (restore arranged inventory + XP)
     */
    public void openGrave(Player player, Grave grave, boolean takeAll) {
        if (!canAccess(player, grave)) {
            Map<String, String> placeholders = placeholders(grave, 1);
            MessageService.send(player, plugin.getConfigManager().prefixed("denied"), placeholders);
            return;
        }

        // Prevent duplication: only one player can interact at a time
        if (!lockedGraves.add(grave.getId())) {
            MessageService.send(player, plugin.getConfigManager().prefixed("denied"), placeholders(grave, 1));
            return;
        }

        playLootSound(player.getLocation());
        giveExperience(player, grave);
        hologram.update(grave);

        if (takeAll || plugin.getConfigManager().autoLoot()) {
            try {
                boolean partial = restoreItemsArranged(player, grave);
                if (grave.isEmpty()) {
                    MessageService.send(player, plugin.getConfigManager().prefixed("looted"), placeholders(grave, 1));
                    removeGrave(grave, false, false);
                } else {
                    if (partial) {
                        MessageService.send(player, plugin.getConfigManager().prefixed("looted-partial"), placeholders(grave, 1));
                    }
                    hologram.update(grave);
                    save();
                }
            } finally {
                lockedGraves.remove(grave.getId());
            }
            return;
        }

        // GUI path: lock is released in handleGuiClose
        GraveGui gui = new GraveGui(plugin, grave);
        gui.open(player);
        if (player.getOpenInventory().getTopInventory() != gui.getInventory()) {
            // Another plugin cancelled the open; no close event will ever release the lock.
            lockedGraves.remove(grave.getId());
        }
    }

    /**
     * Persist GUI changes right after each click (debounced to one save per tick) instead of
     * only on close, so a crash with the GUI open cannot duplicate taken items.
     */
    public void scheduleGuiSync(Player player, GraveGui gui) {
        if (!gui.markSyncPending()) {
            return;
        }
        SchedulerUtils.runForPlayer(plugin, player, () -> {
            gui.clearSyncPending();
            if (!graves.containsKey(gui.getGrave().getId())) {
                return;
            }
            gui.syncFromInventory();
            save();
        });
    }

    public void handleGuiClose(Player player, GraveGui gui) {
        Grave grave = gui.getGrave();
        try {
            if (!graves.containsKey(grave.getId())) {
                return;
            }

            gui.syncFromInventory();

            if (!plugin.isEnabled()) {
                // Shutdown: schedulers reject tasks now; shutdown() does the final sync save and
                // leftover entities of removed graves are cleaned up on the next entities load.
                if (grave.isEmpty()) {
                    graves.remove(grave.getId());
                }
                return;
            }

            if (grave.isEmpty()) {
                MessageService.send(player, plugin.getConfigManager().prefixed("looted"), placeholders(grave, 1));
                removeGrave(grave, false, false);
            } else {
                hologram.update(grave);
                save();
            }
        } finally {
            lockedGraves.remove(grave.getId());
        }
    }

    /**
     * Put items back into the same inventory slots when free; otherwise overflow into free space.
     */
    private boolean restoreItemsArranged(Player player, Grave grave) {
        ItemStack[] items = grave.getItems();
        ItemStack[] remaining = new ItemStack[items.length];
        boolean overflow = false;
        PlayerInventory inv = player.getInventory();

        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || item.getType().isAir()) {
                remaining[i] = null;
                continue;
            }

            ItemStack clone = item.clone();
            ItemStack current = null;
            try {
                current = inv.getItem(i);
            } catch (Exception ignored) {
            }

            if (isEmpty(current)) {
                try {
                    inv.setItem(i, clone);
                    remaining[i] = null;
                    continue;
                } catch (Exception ignored) {
                }
            }

            if (plugin.getConfigManager().autoEquipArmor() && tryEquipArmor(inv, clone)) {
                remaining[i] = null;
                continue;
            }

            HashMap<Integer, ItemStack> left = inv.addItem(clone);
            if (left.isEmpty()) {
                remaining[i] = null;
            } else {
                overflow = true;
                remaining[i] = left.values().iterator().next();
            }
        }

        grave.setItems(remaining);
        return overflow;
    }

    private boolean tryEquipArmor(PlayerInventory inv, ItemStack item) {
        EquipmentSlot slot;
        try {
            slot = item.getType().getEquipmentSlot();
        } catch (Exception ignored) {
            return false;
        }
        return switch (slot) {
            case HEAD -> {
                if (isEmpty(inv.getHelmet())) {
                    inv.setHelmet(item);
                    yield true;
                }
                yield false;
            }
            case CHEST -> {
                if (isEmpty(inv.getChestplate())) {
                    inv.setChestplate(item);
                    yield true;
                }
                yield false;
            }
            case LEGS -> {
                if (isEmpty(inv.getLeggings())) {
                    inv.setLeggings(item);
                    yield true;
                }
                yield false;
            }
            case FEET -> {
                if (isEmpty(inv.getBoots())) {
                    inv.setBoots(item);
                    yield true;
                }
                yield false;
            }
            default -> false;
        };
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    private void giveExperience(Player player, Grave grave) {
        int xp = grave.getExperience();
        if (xp <= 0) {
            return;
        }
        ExperienceUtils.giveExp(player, xp);
        Map<String, String> placeholders = placeholders(grave, 1);
        placeholders.put("xp", String.valueOf(xp));
        MessageService.send(player, plugin.getConfigManager().prefixed("looted-xp"), placeholders);
        grave.setExperience(0);
        save();
    }

    public boolean canAccess(Player player, Grave grave) {
        if (!plugin.getConfigManager().ownerOnly()) {
            return true;
        }
        if (player.getUniqueId().equals(grave.getOwnerId())) {
            return true;
        }
        return player.hasPermission("rexgraves.bypass") || player.hasPermission("rexgraves.admin");
    }

    public void removeGrave(Grave grave, boolean notifyOwner, boolean dropItems) {
        Location location = grave.getLocation();

        // Capture items before removing from map
        ItemStack[] itemsToDrop = dropItems ? grave.getItems() : null;

        SchedulerUtils.runAtLocation(plugin, location == null ? plugin.getServer().getWorlds().get(0).getSpawnLocation() : location,
                () -> {
                    // Drop items on the correct region thread (Folia-safe)
                    if (itemsToDrop != null && location != null && location.getWorld() != null) {
                        for (ItemStack item : itemsToDrop) {
                            if (item != null && !item.getType().isAir()) {
                                location.getWorld().dropItemNaturally(location, item);
                            }
                        }
                    }
                    removeVisuals(grave, true);
                });

        graves.remove(grave.getId());
        if (grave.getMarkerUuid() != null) {
            markerIndex.remove(grave.getMarkerUuid());
        }
        if (grave.getClickBoxUuid() != null) {
            markerIndex.remove(grave.getClickBoxUuid());
        }
        lockedGraves.remove(grave.getId());
        save();

        if (notifyOwner) {
            Player owner = Bukkit.getPlayer(grave.getOwnerId());
            if (owner != null) {
                MessageService.send(owner, plugin.getConfigManager().prefixed("removed"), placeholders(grave, 1));
            }
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        List<Grave> expired = new ArrayList<>();
        for (Grave grave : graves.values()) {
            if (grave.isExpired(now)) {
                expired.add(grave);
            } else {
                Location location = grave.getLocation();
                // Unloaded graves have no entities to update; skip them entirely.
                if (location == null || location.getWorld() == null
                        || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    continue;
                }
                SchedulerUtils.runAtLocation(plugin, location, () -> {
                    // Hologram text / particles only matter if someone can see them.
                    if (location.getNearbyPlayers(VIEW_RANGE).isEmpty()) {
                        return;
                    }
                    hologram.update(grave);
                    if (plugin.getConfigManager().particles()) {
                        location.getWorld().spawnParticle(Particle.SOUL, location.clone().add(0, 1.0, 0), 2, 0.15, 0.2, 0.15, 0.01);
                    }
                });
            }
        }

        boolean drop = "drop".equalsIgnoreCase(plugin.getConfigManager().expireAction());
        for (Grave grave : expired) {
            Player owner = Bukkit.getPlayer(grave.getOwnerId());
            if (owner != null) {
                MessageService.send(owner, plugin.getConfigManager().prefixed("expired"), placeholders(grave, 1));
            }
            removeGrave(grave, false, drop);
        }
    }

    /**
     * Entities load separately from (and after) the chunk on Paper, so this runs on EntitiesLoadEvent.
     * Tagged entities that are not the grave's current visuals are leftovers (e.g. from a crash
     * before the new UUIDs were saved) and get removed so holograms never double up.
     */
    public void handleEntitiesLoad(org.bukkit.Chunk chunk, List<Entity> entities) {
        for (Entity entity : entities) {
            String tagged = GraveKeys.readGraveId(entity.getPersistentDataContainer(), plugin);
            if (tagged == null) {
                continue;
            }
            Grave grave = graves.get(tagged);
            if (grave == null || !isCurrentVisual(grave, entity.getUniqueId())) {
                markerIndex.remove(entity.getUniqueId());
                entity.remove();
            } else if (entity instanceof ArmorStand || entity instanceof Interaction) {
                markerIndex.put(entity.getUniqueId(), tagged);
            }
        }

        for (Grave grave : graves.values()) {
            Location location = grave.getLocation();
            if (location == null || location.getWorld() == null) {
                continue;
            }
            if (!location.getWorld().equals(chunk.getWorld())) {
                continue;
            }
            if (location.getBlockX() >> 4 != chunk.getX() || location.getBlockZ() >> 4 != chunk.getZ()) {
                continue;
            }
            Entity marker = findEntity(grave, grave.getMarkerUuid());
            if (marker == null || marker.isDead()) {
                SchedulerUtils.runAtLocation(plugin, location, () -> spawnVisuals(grave, false));
            }
        }
    }

    private boolean isCurrentVisual(Grave grave, UUID uuid) {
        return uuid.equals(grave.getMarkerUuid())
                || uuid.equals(grave.getClickBoxUuid())
                || grave.getHologramUuids().contains(uuid);
    }

    public Optional<Grave> nearest(Player player) {
        List<Grave> owned = getByOwner(player.getUniqueId());
        if (owned.isEmpty()) {
            return Optional.empty();
        }
        Location origin = player.getLocation();
        return owned.stream()
                .filter(g -> g.getLocation() != null && g.getLocation().getWorld() != null)
                .min(Comparator.comparingDouble(g -> {
                    Location loc = g.getLocation();
                    if (origin.getWorld() == null || !loc.getWorld().equals(origin.getWorld())) {
                        return Double.MAX_VALUE;
                    }
                    return loc.distanceSquared(origin);
                }));
    }

    public Map<String, String> placeholders(Grave grave, int index) {
        Map<String, String> map = new HashMap<>();
        long now = System.currentTimeMillis();
        String timePattern = plugin.getConfigManager().message("time-format");

        map.put("player", grave.getOwnerName());
        map.put("owner", grave.getOwnerName());
        map.put("id", grave.getId());
        map.put("index", String.valueOf(index));
        map.put("world", grave.getWorldName() == null ? "?" : grave.getWorldName());
        map.put("x", String.valueOf((int) Math.floor(grave.getX())));
        map.put("y", String.valueOf((int) Math.floor(grave.getY())));
        map.put("z", String.valueOf((int) Math.floor(grave.getZ())));
        map.put("items", String.valueOf(grave.countItems()));
        map.put("xp", String.valueOf(grave.getExperience()));

        String timeLeft = TimeFormat.formatRemaining(
                grave.remainingMillis(now),
                plugin.getConfigManager().message("time-never"),
                plugin.getConfigManager().message("time-expired"),
                timePattern
        );
        String timeSince = TimeFormat.formatElapsed(Math.max(0L, now - grave.getCreatedAt()), timePattern);

        map.put("time_left", timeLeft);
        map.put("time_since", timeSince);
        map.put("since_died", timeSince);
        map.put("died_ago", timeSince);

        Location loc = grave.getLocation();
        if (loc != null) {
            map.put("distance", "?");
        }
        return map;
    }

    private Entity findEntity(Grave grave, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Location location = grave.getLocation();
        if (location != null && location.getWorld() != null) {
            Entity entity = location.getWorld().getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        for (var world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private void playCreateEffects(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (plugin.getConfigManager().particles()) {
            location.getWorld().spawnParticle(Particle.SOUL, location.clone().add(0, 1.0, 0), 20, 0.3, 0.4, 0.3, 0.02);
            location.getWorld().spawnParticle(Particle.END_ROD, location.clone().add(0, 1.2, 0), 12, 0.2, 0.3, 0.2, 0.01);
        }
        playSound(location, plugin.getConfigManager().createSound());
    }

    private void playLootSound(Location location) {
        playSound(location, plugin.getConfigManager().lootSound());
    }

    private void playSound(Location location, String soundName) {
        if (location == null || location.getWorld() == null || soundName == null || soundName.isBlank()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            location.getWorld().playSound(location, sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public void shutdown() {
        // Close open GUIs so nobody keeps taking items after the final save (handleGuiClose syncs them).
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof GraveGui) {
                player.closeInventory();
            }
        }
        storage.saveAllSync(graves.values());
        storage.close();
    }
}
