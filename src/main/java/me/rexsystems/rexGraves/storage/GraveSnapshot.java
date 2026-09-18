package me.rexsystems.rexGraves.storage;

import me.rexsystems.rexGraves.grave.Grave;
import me.rexsystems.rexGraves.util.ItemSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Immutable row built on the calling thread so item serialization stays off the async writer.
 */
public final class GraveSnapshot {

    public final String id;
    public final String ownerId;
    public final String ownerName;
    public final String world;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;
    public final int experience;
    public final long createdAt;
    public final long expiresAt;
    public final String items;
    public final String markerUuid;
    public final String clickBoxUuid;
    public final String hologramUuids;

    private GraveSnapshot(Grave grave) {
        this.id = grave.getId();
        this.ownerId = grave.getOwnerId().toString();
        this.ownerName = grave.getOwnerName();
        this.world = grave.getWorldName();
        this.x = grave.getX();
        this.y = grave.getY();
        this.z = grave.getZ();
        this.yaw = grave.getYaw();
        this.pitch = grave.getPitch();
        this.experience = grave.getExperience();
        this.createdAt = grave.getCreatedAt();
        this.expiresAt = grave.getExpiresAt();
        this.items = ItemSerializer.serialize(grave.getItems());
        this.markerUuid = grave.getMarkerUuid() == null ? null : grave.getMarkerUuid().toString();
        this.clickBoxUuid = grave.getClickBoxUuid() == null ? null : grave.getClickBoxUuid().toString();
        this.hologramUuids = grave.getHologramUuids().stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));
    }

    public static List<GraveSnapshot> from(Collection<Grave> graves) {
        List<GraveSnapshot> snapshots = new ArrayList<>(graves.size());
        for (Grave grave : new ArrayList<>(graves)) {
            snapshots.add(new GraveSnapshot(grave));
        }
        return snapshots;
    }
}
