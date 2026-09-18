package me.rexsystems.rexGraves.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class LocationUtil {

    private LocationUtil() {
    }

    /**
     * Find a safe grave location near the death point (void / lava / fire friendly).
     * Void deaths stay on the same X/Z and use the first standable Y above world min height.
     */
    public static Location findSafeGraveLocation(Location origin) {
        if (origin == null || origin.getWorld() == null) {
            return origin;
        }

        World world = origin.getWorld();
        Location base = origin.clone();

        if (isVoidDeath(base)) {
            Location voidSafe = findAboveVoid(base);
            if (voidSafe != null) {
                return center(voidSafe);
            }
        }

        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        if (base.getBlockY() < minY) {
            base.setY(minY);
        } else if (base.getBlockY() > maxY) {
            base.setY(maxY);
        }

        if (isSafe(base)) {
            return center(base);
        }

        for (int y = 0; y <= 16; y++) {
            Location up = base.clone().add(0, y, 0);
            if (isSafe(up)) {
                return center(up);
            }
            Location down = base.clone().add(0, -y, 0);
            if (y > 0 && isSafe(down)) {
                return center(down);
            }
        }

        for (int r = 1; r <= 4; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.abs(x) != r && Math.abs(z) != r) {
                        continue;
                    }
                    Location candidate = base.clone().add(x, 0, z);
                    if (isSafe(candidate)) {
                        return center(candidate);
                    }
                    for (int y = 1; y <= 8; y++) {
                        Location up = candidate.clone().add(0, y, 0);
                        if (isSafe(up)) {
                            return center(up);
                        }
                    }
                }
            }
        }

        int highest = world.getHighestBlockYAt(base);
        Location fallback = new Location(world, base.getX(), Math.max(highest + 1, minY), base.getZ());
        return center(fallback);
    }

    private static boolean isVoidDeath(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        int minHeight = world.getMinHeight();
        if (location.getBlockY() < minHeight) {
            return true;
        }
        int x = location.getBlockX();
        int z = location.getBlockZ();
        for (int y = location.getBlockY(); y >= minHeight; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid() || block.isLiquid()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Same X/Z as death, first standable spot scanning up from just above void.
     */
    private static Location findAboveVoid(Location origin) {
        World world = origin.getWorld();
        if (world == null) {
            return null;
        }
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        double x = origin.getX();
        double z = origin.getZ();
        float yaw = origin.getYaw();
        float pitch = origin.getPitch();

        for (int y = minY; y <= maxY; y++) {
            Location candidate = new Location(world, x, y, z, yaw, pitch);
            if (isSafe(candidate)) {
                return candidate;
            }
        }

        // No solid footing in the column: place at the void edge so it stays reachable.
        return new Location(world, x, minY, z, yaw, pitch);
    }

    private static boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        int y = location.getBlockY();
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) {
            return false;
        }

        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block below = feet.getRelative(0, -1, 0);

        if (!isPassable(feet) || !isPassable(head)) {
            return false;
        }
        if (isDangerous(feet) || isDangerous(head) || isDangerous(below)) {
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
