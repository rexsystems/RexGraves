package me.rexsystems.rexGraves.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class LocationUtil {

    private LocationUtil() {
    }

    /**
     * Grave location = the exact death block (lava, fire, mid-air, anywhere).
     * Only deaths below the world floor are moved: same X/Z, first standable Y above the void,
     * because entities below min height get removed by the server.
     */
    public static Location graveLocation(Location origin) {
        if (origin == null || origin.getWorld() == null) {
            return origin;
        }

        World world = origin.getWorld();
        Location base = origin.clone();

        if (base.getBlockY() < world.getMinHeight()) {
            return center(findAboveVoid(base));
        }

        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        if (base.getBlockY() < minY) {
            base.setY(minY);
        } else if (base.getBlockY() > maxY) {
            base.setY(maxY);
        }
        return center(base);
    }

    /**
     * Same X/Z as death, first standable spot scanning up from just above void.
     */
    private static Location findAboveVoid(Location origin) {
        World world = origin.getWorld();
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        double x = origin.getX();
        double z = origin.getZ();
        float yaw = origin.getYaw();
        float pitch = origin.getPitch();

        for (int y = minY; y <= maxY; y++) {
            Location candidate = new Location(world, x, y, z, yaw, pitch);
            if (isStandable(candidate)) {
                return candidate;
            }
        }

        // No solid footing in the column: place at the void edge so it stays reachable.
        return new Location(world, x, minY, z, yaw, pitch);
    }

    private static boolean isStandable(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block below = feet.getRelative(0, -1, 0);

        if (!isPassable(feet) || !isPassable(head)) {
            return false;
        }
        if (isDangerous(below)) {
            return false;
        }
        return below.getType().isSolid() || below.isLiquid();
    }

    private static boolean isPassable(Block block) {
        Material type = block.getType();
        return type.isAir() || (!type.isSolid() && !isDangerous(block));
    }

    private static boolean isDangerous(Block block) {
        Material type = block.getType();
        return type == Material.LAVA
                || type == Material.FIRE
                || type == Material.SOUL_FIRE
                || type == Material.MAGMA_BLOCK
                || type == Material.CACTUS
                || type == Material.SWEET_BERRY_BUSH
                || type == Material.WITHER_ROSE;
    }

    private static Location center(Location location) {
        return new Location(
                location.getWorld(),
                location.getBlockX() + 0.5,
                location.getBlockY(),
                location.getBlockZ() + 0.5,
                location.getYaw(),
                location.getPitch()
        );
    }
}
