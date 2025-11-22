package pl.tremeq.simplesession.milestone;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Represents a session milestone that can be achieved by players.
 *
 * Milestones are triggered when a player reaches a specific session duration.
 * They can send messages and execute commands as rewards.
 *
 * @author TremeQ
 */
public class Milestone {

    private final String id;
    private final int timeSeconds;
    private final String message;
    private final List<String> commands;

    /**
     * Creates a new milestone.
     *
     * @param id Unique identifier for this milestone
     * @param timeSeconds Required session time in seconds
     * @param message Message to send to player (supports color codes)
     * @param commands Commands to execute (supports {player} placeholder)
     */
    public Milestone(String id, int timeSeconds, String message, List<String> commands) {
        this.id = id;
        this.timeSeconds = timeSeconds;
        this.message = message;
        this.commands = commands;
    }

    /**
     * Gets the milestone identifier.
     *
     * @return Milestone ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the required session time in seconds.
     *
     * @return Time in seconds
     */
    public int getTimeSeconds() {
        return timeSeconds;
    }

    /**
     * Gets the milestone message.
     *
     * @return Message string
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the list of commands to execute.
     *
     * @return List of command strings
     */
    public List<String> getCommands() {
        return commands;
    }

    /**
     * Executes this milestone for a player.
     * Sends the message and runs all commands.
     *
     * @param player The player who achieved this milestone
     */
    public void execute(Player player) {
        // Send message if configured
        if (message != null && !message.isEmpty()) {
            String formattedMessage = ChatColor.translateAlternateColorCodes('&', message)
                    .replace("{player}", player.getName())
                    .replace("{time}", formatTime(timeSeconds));
            player.sendMessage(formattedMessage);
        }

        // Execute commands if configured
        if (commands != null && !commands.isEmpty()) {
            for (String command : commands) {
                String formattedCommand = command
                        .replace("{player}", player.getName())
                        .replace("{uuid}", player.getUniqueId().toString());

                // Execute command from console
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("SimpleSession"), () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCommand);
                });
            }
        }
    }

    /**
     * Formats time in seconds to a readable string.
     *
     * @param seconds Time in seconds
     * @return Formatted time string (e.g., "1h 30m")
     */
    private String formatTime(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else if (minutes > 0) {
            return minutes + "m " + secs + "s";
        } else {
            return secs + "s";
        }
    }

    @Override
    public String toString() {
        return "Milestone{id='" + id + "', time=" + timeSeconds + "s}";
    }
}
