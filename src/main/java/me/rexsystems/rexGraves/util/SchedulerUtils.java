package me.rexsystems.rexGraves.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Folia-compatible scheduling helpers.
 */
public final class SchedulerUtils {

    private SchedulerUtils() {
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    public static void runSync(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
    }

    public static void runLater(Plugin plugin, Runnable task, long delayTicks) {
        long delay = Math.max(1L, delayTicks);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delay);
    }

    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (location == null || location.getWorld() == null) {
            runSync(plugin, task);
            return;
        }
        Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
    }

    public static void runAtLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (location == null || location.getWorld() == null) {
            runLater(plugin, task, delayTicks);
            return;
        }
        long delay = Math.max(1L, delayTicks);
        Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), delay);
    }

    public static void runAtFixedRate(Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        long initial = Math.max(1L, initialDelayTicks);
        long period = Math.max(1L, periodTicks);
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), initial, period);
    }

    public static void runForPlayer(Plugin plugin, Player player, Runnable task) {
        if (player == null) {
            return;
        }
        player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    public static void runForEntity(Plugin plugin, Entity entity, Runnable task) {
        if (entity == null) {
            return;
        }
        entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }
}
