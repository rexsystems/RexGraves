package me.rexsystems.rexGraves.grave;

import me.rexsystems.rexGraves.RexGraves;
import me.rexsystems.rexGraves.util.GraveKeys;
import me.rexsystems.rexGraves.util.MessageService;
import me.rexsystems.rexGraves.util.PapiUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One TextDisplay hologram with multiple lines (newline-separated).
 */
public class GraveHologram {

    private final RexGraves plugin;
    /** Last text pushed per grave, so unchanged holograms skip MiniMessage parsing and entity updates. */
    private final Map<String, String> lastRendered = new ConcurrentHashMap<>();

    public GraveHologram(RexGraves plugin) {
        this.plugin = plugin;
    }

    public List<UUID> spawn(Grave grave) {
        List<UUID> ids = new ArrayList<>();
        if (!plugin.getConfigManager().hologramEnabled()) {
            return ids;
        }
        Location base = grave.getLocation();
        if (base == null || base.getWorld() == null) {
            return ids;
        }

        List<String> lines = plugin.getConfigManager().hologramLines();
        if (lines.isEmpty()) {
            return ids;
        }

        double offsetY = plugin.getConfigManager().hologramOffsetY();
        Location lineLoc = base.clone().add(0, offsetY, 0);
        Component text = buildText(grave);

        TextDisplay display = base.getWorld().spawn(lineLoc, TextDisplay.class, entity -> {
            entity.text(text);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setShadowed(true);
            entity.setSeeThrough(true);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setLineWidth(200);
            entity.setPersistent(true);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            GraveKeys.tagHologram(entity.getPersistentDataContainer(), plugin, grave.getId());
            try {
                entity.setTeleportDuration(0);
            } catch (NoSuchMethodError ignored) {
            }
        });
        ids.add(display.getUniqueId());
        return ids;
    }

    public void update(Grave grave) {
        if (!plugin.getConfigManager().hologramEnabled()) {
            return;
        }
        List<UUID> holograms = grave.getHologramUuids();
        if (holograms.isEmpty()) {
            return;
        }
        Entity entity = findEntity(grave, holograms.get(0));
        if (!(entity instanceof TextDisplay display)) {
            return;
        }
        List<String> rendered = renderLines(grave);
        String key = String.join("\n", rendered);
        if (key.equals(lastRendered.put(grave.getId(), key))) {
            return;
        }
        display.text(toComponent(rendered));
    }

    public void remove(Grave grave) {
        lastRendered.remove(grave.getId());
        Location location = grave.getLocation();
        for (UUID uuid : new ArrayList<>(grave.getHologramUuids())) {
            Entity entity = findEntity(grave, uuid);
            if (entity != null) {
                entity.remove();
            } else if (location != null && location.getWorld() != null) {
                for (Entity nearby : location.getWorld().getNearbyEntities(location, 3, 4, 3)) {
                    if (nearby.getUniqueId().equals(uuid)) {
                        nearby.remove();
                        break;
                    }
                }
            }
        }
        grave.getHologramUuids().clear();
    }

    private Component buildText(Grave grave) {
        List<String> rendered = renderLines(grave);
        lastRendered.put(grave.getId(), String.join("\n", rendered));
        return toComponent(rendered);
    }

    private List<String> renderLines(Grave grave) {
        List<String> lines = plugin.getConfigManager().hologramLines();
        Map<String, String> placeholders = plugin.getGraveManager().placeholders(grave, 1);
        OfflinePlayer owner = Bukkit.getOfflinePlayer(grave.getOwnerId());

        List<String> rendered = new ArrayList<>(lines.size());
        for (String raw : lines) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            rendered.add(renderLine(raw, placeholders, owner));
        }
        return rendered;
    }

    private static Component toComponent(List<String> rendered) {
        Component text = Component.empty();
        boolean first = true;
        for (String line : rendered) {
            if (!first) {
                text = text.append(Component.newline());
            }
            text = text.append(MessageService.parse(line));
            first = false;
        }
        return text;
    }

    private String renderLine(String raw, Map<String, String> placeholders, OfflinePlayer owner) {
        String value = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String replacement = entry.getValue() == null ? "" : entry.getValue();
            value = value.replace("{" + entry.getKey() + "}", replacement);
            value = value.replace("%" + entry.getKey() + "%", replacement);
        }
        return PapiUtils.apply(owner, value);
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
}
