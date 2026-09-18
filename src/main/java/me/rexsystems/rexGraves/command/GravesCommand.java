package me.rexsystems.rexGraves.command;

import me.rexsystems.rexGraves.RexGraves;
import me.rexsystems.rexGraves.convert.AxGravesConverter;
import me.rexsystems.rexGraves.grave.Grave;
import me.rexsystems.rexGraves.util.MessageService;
import me.rexsystems.rexGraves.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.io.File;

public class GravesCommand implements CommandExecutor, TabCompleter {

    private final RexGraves plugin;

    public GravesCommand(RexGraves plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return help(sender);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "help" -> help(sender);
            case "reload" -> reload(sender);
            case "list" -> list(sender, args);
            case "locate" -> locate(sender);
            case "tp", "teleport" -> teleport(sender, args);
            case "compass" -> compass(sender, args);
            case "admin" -> admin(sender, args);
            default -> help(sender);
        };
    }

    private boolean help(CommandSender sender) {
        if (!sender.hasPermission("rexgraves.use") && !sender.hasPermission("rexgraves.admin")) {
            deny(sender);
            return true;
        }
        MessageService.send(sender, plugin.getConfigManager().prefixed("help-header"));
        sendHelpLine(sender, "help", "Show this help");
        sendHelpLine(sender, "list", "List your graves");
        sendHelpLine(sender, "locate", "Locate your nearest grave");
        sendHelpLine(sender, "tp [index]", "Teleport to a grave");
        sendHelpLine(sender, "compass [index]", "Point compass to a grave");
        if (sender.hasPermission("rexgraves.reload")) {
            sendHelpLine(sender, "reload", "Reload configuration");
        }
        if (sender.hasPermission("rexgraves.admin")) {
            sendHelpLine(sender, "admin list [player] [page]", "List all graves or a player's");
            sendHelpLine(sender, "admin remove <id>", "Remove a grave");
            sendHelpLine(sender, "admin tp <id>", "Teleport to any grave");
            sendHelpLine(sender, "admin convert axgraves [path]", "Import AxGraves data.json");
        }
        return true;
    }

    private void sendHelpLine(CommandSender sender, String cmd, String description) {
        Map<String, String> map = new HashMap<>();
        map.put("command", cmd);
        map.put("description", description);
        MessageService.send(sender, plugin.getConfigManager().prefixed("help-line"), map);
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("rexgraves.reload")) {
            deny(sender);
            return true;
        }
        plugin.reloadPlugin();
        MessageService.send(sender, plugin.getConfigManager().prefixed("reload"));
        return true;
    }

    private boolean list(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("rexgraves.list")) {
            deny(sender);
            return true;
        }

        List<Grave> graves = plugin.getGraveManager().getByOwner(player.getUniqueId());
        if (graves.isEmpty()) {
            MessageService.send(player, plugin.getConfigManager().prefixed("none"));
            return true;
        }

        Map<String, String> header = new HashMap<>();
        header.put("count", String.valueOf(graves.size()));
        MessageService.send(player, plugin.getConfigManager().prefixed("list-header"), header);

        int index = 1;
        for (Grave grave : graves) {
            MessageService.send(player, plugin.getConfigManager().prefixed("list-entry"),
                    plugin.getGraveManager().placeholders(grave, index++));
        }
        return true;
    }

    private boolean locate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("rexgraves.locate")) {
            deny(sender);
            return true;
        }

        Optional<Grave> nearest = plugin.getGraveManager().nearest(player);
        if (nearest.isEmpty()) {
            MessageService.send(player, plugin.getConfigManager().prefixed("none"));
            return true;
        }

        Grave grave = nearest.get();
        Map<String, String> placeholders = plugin.getGraveManager().placeholders(grave, 1);
        Location loc = grave.getLocation();
        if (loc != null && loc.getWorld() != null && loc.getWorld().equals(player.getWorld())) {
            placeholders.put("distance", String.valueOf((int) Math.round(player.getLocation().distance(loc))));
        } else {
            placeholders.put("distance", "?");
        }
        MessageService.send(player, plugin.getConfigManager().prefixed("locate"), placeholders);
        return true;
    }

    private boolean teleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("rexgraves.teleport")) {
            deny(sender);
            return true;
        }

        List<Grave> graves = plugin.getGraveManager().getByOwner(player.getUniqueId());
        if (graves.isEmpty()) {
            MessageService.send(player, plugin.getConfigManager().prefixed("none"));
            return true;
        }

        int index = 1;
        if (args.length >= 2) {
            try {
                index = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                MessageService.send(player, plugin.getConfigManager().prefixed("not-found"));
                return true;
            }
        }
        if (index < 1 || index > graves.size()) {
            MessageService.send(player, plugin.getConfigManager().prefixed("not-found"));
            return true;
        }

        Grave grave = graves.get(index - 1);
        Location location = grave.getLocation();
        if (location == null) {
            MessageService.send(player, plugin.getConfigManager().prefixed("not-found"));
            return true;
        }

        int finalIndex = index;
        SchedulerUtils.runAtLocation(plugin, location, () -> {
            player.teleportAsync(location.clone().add(0, 1, 0)).thenAccept(success -> {
                if (Boolean.TRUE.equals(success)) {
                    MessageService.send(player, plugin.getConfigManager().prefixed("teleported"),
                            plugin.getGraveManager().placeholders(grave, finalIndex));
                }
            });
        });
        return true;
    }

    private boolean compass(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("rexgraves.compass")) {
            deny(sender);
            return true;
        }

        List<Grave> graves = plugin.getGraveManager().getByOwner(player.getUniqueId());
        if (graves.isEmpty()) {
            MessageService.send(player, plugin.getConfigManager().prefixed("none"));
            return true;
        }

        int index = 1;
        if (args.length >= 2) {
            try {
                index = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                MessageService.send(player, plugin.getConfigManager().prefixed("not-found"));
                return true;
            }
        }
        if (index < 1 || index > graves.size()) {
            MessageService.send(player, plugin.getConfigManager().prefixed("not-found"));
            return true;
        }

        Grave grave = graves.get(index - 1);
        Location location = grave.getLocation();
        if (location == null) {
            MessageService.send(player, plugin.getConfigManager().prefixed("not-found"));
            return true;
        }

        player.setCompassTarget(location);
        MessageService.send(player, plugin.getConfigManager().prefixed("compass"),
                plugin.getGraveManager().placeholders(grave, index));
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rexgraves.admin")) {
            deny(sender);
            return true;
        }
        if (args.length < 2) {
            sendHelpLine(sender, "admin list [player] [page]", "List all graves or a player's");
            sendHelpLine(sender, "admin remove <id>", "Remove a grave");
            sendHelpLine(sender, "admin tp <id>", "Teleport to any grave");
            sendHelpLine(sender, "admin convert axgraves [path]", "Import AxGraves data.json");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "list" -> adminList(sender, args);
            case "remove" -> adminRemove(sender, args);
            case "tp", "teleport" -> adminTp(sender, args);
            case "convert", "import" -> adminConvert(sender, args);
            default -> {
                help(sender);
                yield true;
            }
        };
    }

    private boolean adminConvert(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[2].equalsIgnoreCase("axgraves")) {
            MessageService.send(sender, plugin.getConfigManager().prefixed("convert-usage"));
            return true;
        }

        File file;
        if (args.length >= 4) {
            file = new File(args[3]);
            if (!file.isAbsolute()) {
                file = new File(plugin.getDataFolder(), args[3]);
            }
        } else {
            file = AxGravesConverter.resolveDefaultFile(plugin);
        }

        AxGravesConverter.Result result = new AxGravesConverter(plugin).convert(file);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("path", file.getAbsolutePath());
        placeholders.put("imported", String.valueOf(result.imported()));
        placeholders.put("skipped", String.valueOf(result.skipped()));
        placeholders.put("error", result.error() == null ? "" : result.error());

        if (!file.isFile()) {
            MessageService.send(sender, plugin.getConfigManager().prefixed("convert-missing"), placeholders);
            return true;
        }
        if (!result.success()) {
            MessageService.send(sender, plugin.getConfigManager().prefixed("convert-failed"), placeholders);
            return true;
        }

        MessageService.send(sender, plugin.getConfigManager().prefixed("convert-done"), placeholders);
        plugin.getLogger().info("AxGraves convert: imported " + result.imported()
                + ", skipped " + result.skipped() + " from " + file.getAbsolutePath());
        return true;
    }

    private static final int ADMIN_LIST_PAGE_SIZE = 8;

    private boolean adminList(CommandSender sender, String[] args) {
        // /admin list
        // /admin list <page>
        // /admin list <player> [page]
        OfflinePlayer target = null;
        int page = 1;

        if (args.length >= 3) {
            if (isPositiveInt(args[2])) {
                page = Integer.parseInt(args[2]);
            } else {
                target = resolveKnownPlayer(args[2]);
                if (target == null) {
                    Map<String, String> missing = new HashMap<>();
                    missing.put("player", args[2]);
                    MessageService.send(sender, plugin.getConfigManager().prefixed("admin-player-not-found"), missing);
                    return true;
                }
                if (args.length >= 4) {
                    if (!isPositiveInt(args[3])) {
                        MessageService.send(sender, plugin.getConfigManager().prefixed("admin-list-usage"));
                        return true;
                    }
                    page = Integer.parseInt(args[3]);
                }
            }
        }

        List<Grave> graves = target == null
                ? plugin.getGraveManager().getAllSorted()
                : plugin.getGraveManager().getByOwner(target.getUniqueId());

        if (graves.isEmpty()) {
            if (target != null) {
                Map<String, String> none = new HashMap<>();
                none.put("player", displayName(target, args.length >= 3 ? args[2] : "?"));
                MessageService.send(sender, plugin.getConfigManager().prefixed("admin-none"), none);
            } else {
                MessageService.send(sender, plugin.getConfigManager().prefixed("admin-none-all"));
            }
            return true;
        }

        int totalPages = Math.max(1, (int) Math.ceil(graves.size() / (double) ADMIN_LIST_PAGE_SIZE));
        if (page > totalPages) {
            page = totalPages;
        }

        int from = (page - 1) * ADMIN_LIST_PAGE_SIZE;
        int to = Math.min(from + ADMIN_LIST_PAGE_SIZE, graves.size());

        Map<String, String> header = new HashMap<>();
        header.put("count", String.valueOf(graves.size()));
        header.put("page", String.valueOf(page));
        header.put("pages", String.valueOf(totalPages));
        if (target == null) {
            MessageService.send(sender, plugin.getConfigManager().prefixed("admin-list-all-header"), header);
        } else {
            header.put("player", displayName(target, args[2]));
            MessageService.send(sender, plugin.getConfigManager().prefixed("admin-list-header"), header);
        }

        for (int i = from; i < to; i++) {
            Grave grave = graves.get(i);
            int index = i + 1;
            Map<String, String> placeholders = plugin.getGraveManager().placeholders(grave, index);
            placeholders.put("id", grave.getId());
            String entryKey = target == null ? "admin-list-all-entry" : "admin-list-entry";
            MessageService.send(sender, plugin.getConfigManager().prefixed(entryKey), placeholders);
        }

        if (totalPages > 1) {
            Map<String, String> footer = new HashMap<>();
            footer.put("page", String.valueOf(page));
            footer.put("pages", String.valueOf(totalPages));
            if (target == null) {
                footer.put("next", page < totalPages
                        ? "/rexgraves admin list " + (page + 1)
                        : "/rexgraves admin list " + page);
            } else {
                String name = displayName(target, args[2]);
                footer.put("next", page < totalPages
                        ? "/rexgraves admin list " + name + " " + (page + 1)
                        : "/rexgraves admin list " + name + " " + page);
            }
            MessageService.send(sender, plugin.getConfigManager().prefixed("admin-list-page"), footer);
        }
        return true;
    }

    private static boolean isPositiveInt(String raw) {
        try {
            return Integer.parseInt(raw) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String displayName(OfflinePlayer player, String fallback) {
        if (player.getName() != null && !player.getName().isBlank()) {
            return player.getName();
        }
        return fallback == null || fallback.isBlank() ? "Unknown" : fallback;
    }

    /**
     * Resolve a real known player: online, grave owner match, or hasPlayedBefore.
     * Avoids inventing offline UUIDs for random names.
     */
    private OfflinePlayer resolveKnownPlayer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }

        for (Grave grave : plugin.getGraveManager().getAll()) {
            if (grave.getOwnerName() != null && grave.getOwnerName().equalsIgnoreCase(name)) {
                return Bukkit.getOfflinePlayer(grave.getOwnerId());
            }
        }

        try {
            UUID uuid = UUID.fromString(name);
            OfflinePlayer byId = Bukkit.getOfflinePlayer(uuid);
            if (byId.isOnline() || byId.hasPlayedBefore() || !plugin.getGraveManager().getByOwner(uuid).isEmpty()) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
        }

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)
                    && (offline.hasPlayedBefore() || offline.isOnline())) {
                return offline;
            }
        }
        return null;
    }

    private boolean adminRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /rexgraves admin remove <id>");
            return true;
        }
        Optional<Grave> graveOpt = plugin.getGraveManager().get(args[2]);
        if (graveOpt.isEmpty()) {
            MessageService.send(sender, plugin.getConfigManager().prefixed("not-found"));
            return true;
        }
        Grave grave = graveOpt.get();
        plugin.getGraveManager().removeGrave(grave, true, false);
        Map<String, String> placeholders = plugin.getGraveManager().placeholders(grave, 1);
        MessageService.send(sender, plugin.getConfigManager().prefixed("admin-removed"), placeholders);
        return true;
    }

    private boolean adminTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /rexgraves admin tp <id>");
            return true;
        }
        Optional<Grave> graveOpt = plugin.getGraveManager().get(args[2]);
        if (graveOpt.isEmpty()) {
            MessageService.send(player, plugin.getConfigManager().prefixed("not-found"));
            return true;
        }
        Grave grave = graveOpt.get();
        Location location = grave.getLocation();
        if (location == null) {
            MessageService.send(player, plugin.getConfigManager().prefixed("not-found"));
            return true;
        }
        SchedulerUtils.runAtLocation(plugin, location, () ->
                player.teleportAsync(location.clone().add(0, 1, 0)).thenAccept(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        MessageService.send(player, plugin.getConfigManager().prefixed("teleported"),
                                plugin.getGraveManager().placeholders(grave, 1));
                    }
                })
        );
        return true;
    }

    private void deny(CommandSender sender) {
        MessageService.send(sender, plugin.getConfigManager().prefixed("no-permission"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("help", "list", "locate", "tp", "compass"));
            if (sender.hasPermission("rexgraves.reload")) {
                options.add("reload");
            }
            if (sender.hasPermission("rexgraves.admin")) {
                options.add("admin");
            }
            return filter(options, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("rexgraves.admin")) {
            return filter(Arrays.asList("list", "remove", "tp", "convert"), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("list")) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("1");
            for (Player online : Bukkit.getOnlinePlayers()) {
                suggestions.add(online.getName());
            }
            for (Grave grave : plugin.getGraveManager().getAll()) {
                if (grave.getOwnerName() != null && !suggestions.contains(grave.getOwnerName())) {
                    suggestions.add(grave.getOwnerName());
                }
            }
            return filter(suggestions, args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("list")) {
            return filter(List.of("1", "2", "3"), args[3]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("convert")) {
            return filter(List.of("axgraves"), args[2]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("tp"))) {
            return filter(plugin.getGraveManager().getAll().stream().map(Grave::getId).collect(Collectors.toList()), args[2]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("compass"))
                && sender instanceof Player player) {
            List<Grave> graves = plugin.getGraveManager().getByOwner(player.getUniqueId());
            List<String> indexes = new ArrayList<>();
            for (int i = 1; i <= graves.size(); i++) {
                indexes.add(String.valueOf(i));
            }
            return filter(indexes, args[1]);
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        String needle = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(needle))
                .collect(Collectors.toList());
    }
}
