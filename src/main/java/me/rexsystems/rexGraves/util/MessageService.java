package me.rexsystems.rexGraves.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MiniMessage message helper with legacy / hex fallbacks.
 */
public final class MessageService {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP =
            LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION =
            LegacyComponentSerializer.legacySection();

    private static final Pattern HEX_AMP = Pattern.compile("&#([A-Fa-f0-9]{6})");
    // Match standalone #RRGGBB, but never MiniMessage color args like gradient:#RRGGBB or <#RRGGBB>
    private static final Pattern HEX_HASH = Pattern.compile("(?<![<:])#([A-Fa-f0-9]{6})");

    private MessageService() {
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String normalized = normalize(input);
        try {
            return MINI.deserialize(normalized);
        } catch (Exception ignored) {
            return LEGACY_AMP.deserialize(input.replace('§', '&'));
        }
    }

    public static Component parse(String input, Map<String, String> placeholders) {
        String value = input == null ? "" : input;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String key = entry.getKey();
                String replacement = entry.getValue() == null ? "" : entry.getValue();
                value = value.replace("{" + key + "}", replacement);
                value = value.replace("%" + key + "%", replacement);
            }
        }
        return parse(value);
    }

    public static void send(CommandSender sender, String message) {
        if (sender == null || message == null || message.isBlank()) {
            return;
        }
        sender.sendMessage(parse(message));
    }

    public static void send(CommandSender sender, String message, Map<String, String> placeholders) {
        if (sender == null || message == null || message.isBlank()) {
            return;
        }
        sender.sendMessage(parse(message, placeholders));
    }

    public static String stringify(Component component) {
        return LEGACY_SECTION.serialize(component);
    }

    private static String normalize(String input) {
        String value = input.replace('§', '&');

        Matcher amp = HEX_AMP.matcher(value);
        StringBuffer ampOut = new StringBuffer();
        while (amp.find()) {
            amp.appendReplacement(ampOut, "<#" + amp.group(1) + ">");
        }
        amp.appendTail(ampOut);
        value = ampOut.toString();

        Matcher hash = HEX_HASH.matcher(value);
        StringBuffer hashOut = new StringBuffer();
        while (hash.find()) {
            hash.appendReplacement(hashOut, "<#" + hash.group(1) + ">");
        }
        hash.appendTail(hashOut);
        value = hashOut.toString();

        // Convert simple legacy codes to MiniMessage where possible
        value = value
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&l", "<bold>")
                .replace("&o", "<italic>")
                .replace("&n", "<underlined>")
                .replace("&m", "<strikethrough>")
                .replace("&k", "<obfuscated>")
                .replace("&r", "<reset>");

        return value;
    }

    public static void actionBar(Player player, String message, Map<String, String> placeholders) {
        if (player == null || message == null) {
            return;
        }
        player.sendActionBar(parse(message, placeholders));
    }
}
