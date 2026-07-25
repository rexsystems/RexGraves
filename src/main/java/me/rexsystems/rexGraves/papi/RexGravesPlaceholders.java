package me.rexsystems.rexGraves.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.rexsystems.rexGraves.RexGraves;
import me.rexsystems.rexGraves.grave.Grave;
import me.rexsystems.rexGraves.util.TimeFormat;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class RexGravesPlaceholders extends PlaceholderExpansion {

    private final RexGraves plugin;

    public RexGravesPlaceholders(RexGraves plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "rexgraves";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return null;
        }

        String key = params.toLowerCase(Locale.ROOT);
        List<Grave> graves = plugin.getGraveManager().getByOwner(player.getUniqueId());

        return switch (key) {
            case "count", "graves" -> String.valueOf(graves.size());
            case "has_grave", "hasgrave" -> String.valueOf(!graves.isEmpty());
            case "nearest_world", "world" -> nearest(player, graves).map(Grave::getWorldName).orElse("");
            case "nearest_x", "x" -> nearest(player, graves).map(g -> String.valueOf((int) Math.floor(g.getX()))).orElse("");
            case "nearest_y", "y" -> nearest(player, graves).map(g -> String.valueOf((int) Math.floor(g.getY()))).orElse("");
            case "nearest_z", "z" -> nearest(player, graves).map(g -> String.valueOf((int) Math.floor(g.getZ()))).orElse("");
            case "nearest_id", "id" -> nearest(player, graves).map(Grave::getId).orElse("");
            case "nearest_items", "items" -> nearest(player, graves).map(g -> String.valueOf(g.countItems())).orElse("0");
            case "nearest_xp", "xp" -> nearest(player, graves).map(g -> String.valueOf(g.getExperience())).orElse("0");
            case "nearest_time_left", "time_left" -> nearest(player, graves)
                    .map(this::formatTimeLeft)
                    .orElse("");
            case "nearest_time_since", "time_since", "since_died", "died_ago" -> nearest(player, graves)
                    .map(this::formatTimeSince)
                    .orElse("");
            case "nearest_distance", "distance" -> distance(player, graves);
            default -> null;
        };
    }

    private Optional<Grave> nearest(OfflinePlayer player, List<Grave> graves) {
        if (graves.isEmpty()) {
            return Optional.empty();
        }
        if (player.isOnline() && player.getPlayer() != null) {
            return plugin.getGraveManager().nearest(player.getPlayer());
        }
        return Optional.of(graves.get(graves.size() - 1));
    }

    private String distance(OfflinePlayer player, List<Grave> graves) {
        if (!(player instanceof Player online)) {
            return "";
        }
        Optional<Grave> nearest = nearest(player, graves);
        if (nearest.isEmpty()) {
            return "";
        }
        Location loc = nearest.get().getLocation();
        if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(online.getWorld())) {
            return "";
        }
        return String.valueOf((int) Math.round(online.getLocation().distance(loc)));
    }

    private String formatTimeLeft(Grave grave) {
        return TimeFormat.formatRemaining(
                grave.remainingMillis(System.currentTimeMillis()),
                plugin.getConfigManager().message("time-never"),
                plugin.getConfigManager().message("time-expired"),
                plugin.getConfigManager().message("time-format")
        );
    }

    private String formatTimeSince(Grave grave) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - grave.getCreatedAt());
        return TimeFormat.formatElapsed(elapsed, plugin.getConfigManager().message("time-format"));
    }
}
