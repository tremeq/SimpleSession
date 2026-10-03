package pl.tremeq.simplesession.milestone;

import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.tremeq.simplesession.SimpleSession;
import pl.tremeq.simplesession.util.Text;

import java.util.List;
import java.util.Locale;

/**
 * Represents a session milestone that can be achieved by players.
 *
 * Milestones are triggered when a player reaches a specific session duration.
 * They can send a message, broadcast a message and run actions.
 *
 * Actions (entries of "commands"):
 * - "[console] cmd" or plain "cmd" - command run by the console
 * - "[player] cmd"                 - command run by the player
 * - "[broadcast] text"             - message to all players
 * - "[message] text"               - message to the player
 * - "[actionbar] text"             - action bar message
 * - "[title] title;subtitle"       - title on screen
 * - "[sound] key;volume;pitch"     - sound, e.g. "entity.player.levelup;1;1"
 *
 * Placeholders: {player}, {uuid}, {time} (milestone time), {session} (current session),
 * plus PlaceholderAPI placeholders in texts.
 *
 * @author TremeQ
 */
public class Milestone {

    private final String id;
    private final long timeSeconds;
    private final String message;
    private final String broadcast;
    private final List<String> commands;
    private final String permission;

    /**
     * Creates a new milestone.
     *
     * @param id Unique identifier for this milestone
     * @param timeSeconds Required session time in seconds
     * @param message Message sent to the player (may be empty)
     * @param broadcast Message sent to everyone (may be empty)
     * @param commands Actions to run
     * @param permission Required permission (null or empty = everyone)
     */
    public Milestone(String id, long timeSeconds, String message, String broadcast, List<String> commands, String permission) {
        this.id = id;
        this.timeSeconds = timeSeconds;
        this.message = message == null ? "" : message;
        this.broadcast = broadcast == null ? "" : broadcast;
        this.commands = commands == null ? List.of() : List.copyOf(commands);
        this.permission = permission == null || permission.isBlank() ? null : permission;
    }

    public String getId() {
        return id;
    }

    public long getTimeSeconds() {
        return timeSeconds;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getCommands() {
        return commands;
    }

    /**
     * @param player Player
     * @return true if the player may receive this milestone
     */
    public boolean canReceive(Player player) {
        return permission == null || player.hasPermission(permission);
    }

    /**
     * Executes this milestone for a player. Main thread only.
     *
     * @param plugin Main plugin instance
     * @param player The player who achieved this milestone
     * @param sessionSeconds Current session length
     */
    public void execute(SimpleSession plugin, Player player, long sessionSeconds) {
        String[] placeholders = {
                "{player}", player.getName(),
                "{uuid}", player.getUniqueId().toString(),
                "{time}", plugin.getFormatManager().display("milestone", timeSeconds),
                "{session}", plugin.getFormatManager().display("milestone", sessionSeconds)
        };

        if (!message.isEmpty()) {
            player.sendMessage(render(plugin, player, message, placeholders));
        }
        if (!broadcast.isEmpty()) {
            Bukkit.broadcastMessage(render(plugin, player, broadcast, placeholders));
        }
        for (String command : commands) {
            try {
                runAction(plugin, player, Text.replace(command, placeholders), placeholders);
            } catch (Exception e) {
                plugin.getLogger().warning("Milestone '" + id + "' action failed: " + command + " (" + e.getMessage() + ")");
            }
        }
    }

    private void runAction(SimpleSession plugin, Player player, String action, String[] placeholders) {
        String trimmed = action.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (lower.startsWith("[player]")) {
            player.performCommand(stripSlash(trimmed.substring(8).trim()));
        } else if (lower.startsWith("[console]")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(trimmed.substring(9).trim()));
        } else if (lower.startsWith("[broadcast]")) {
            Bukkit.broadcastMessage(render(plugin, player, trimmed.substring(11).trim(), placeholders));
        } else if (lower.startsWith("[message]")) {
            player.sendMessage(render(plugin, player, trimmed.substring(9).trim(), placeholders));
        } else if (lower.startsWith("[actionbar]")) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(render(plugin, player, trimmed.substring(11).trim(), placeholders)));
        } else if (lower.startsWith("[title]")) {
            String[] parts = trimmed.substring(7).trim().split(";", 2);
            String title = render(plugin, player, parts[0], placeholders);
            String subtitle = parts.length > 1 ? render(plugin, player, parts[1], placeholders) : "";
            player.sendTitle(title, subtitle, 10, 60, 20);
        } else if (lower.startsWith("[sound]")) {
            String[] parts = trimmed.substring(7).trim().split(";");
            float volume = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 1f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 1f;
            player.playSound(player.getLocation(), parts[0].trim().toLowerCase(Locale.ROOT), volume, pitch);
        } else if (lower.startsWith("broadcast ") && Bukkit.getPluginCommand("broadcast") == null) {
            // Compatibility with 1.0.0 configs: plain Paper/Spigot has no /broadcast command
            Bukkit.broadcastMessage(render(plugin, player, trimmed.substring(10), placeholders));
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(trimmed));
        }
    }

    private static String render(SimpleSession plugin, Player player, String text, String[] placeholders) {
        String result = Text.replace(text, placeholders);
        if (plugin.isPlaceholderAPIEnabled() && result.indexOf('%') >= 0) {
            result = PlaceholderAPI.setPlaceholders(player, result);
        }
        return Text.color(result);
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    @Override
    public String toString() {
        return "Milestone{id='" + id + "', time=" + timeSeconds + "s}";
    }
}
