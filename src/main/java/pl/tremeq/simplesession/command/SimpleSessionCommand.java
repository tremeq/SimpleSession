package pl.tremeq.simplesession.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.tremeq.simplesession.SimpleSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main command executor for SimpleSession plugin.
 *
 * Handles all plugin commands including reload, info, help, and debug.
 *
 * @author TremeQ
 */
public class SimpleSessionCommand implements CommandExecutor, TabCompleter {

    private final SimpleSession plugin;

    /**
     * Creates a new command executor.
     *
     * @param plugin The main plugin instance
     */
    public SimpleSessionCommand(SimpleSession plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes the command.
     *
     * @param sender Source of the command
     * @param command Command which was executed
     * @param label Alias of the command used
     * @param args Passed command arguments
     * @return true if valid command, false otherwise
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // No arguments - show help
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                handleReload(sender);
                break;

            case "info":
            case "version":
                handleInfo(sender);
                break;

            case "debug":
                handleDebug(sender);
                break;

            case "top":
                handleTop(sender);
                break;

            case "help":
                sendHelp(sender);
                break;

            default:
                sender.sendMessage(plugin.getMessageManager().getMessage("commands.unknown-command"));
                return true;
        }

        return true;
    }

    /**
     * Handles the reload subcommand.
     * Reloads the plugin configuration.
     *
     * @param sender Command sender
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("simplesession.admin")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("commands.no-permission"));
            return;
        }

        try {
            plugin.reloadConfig();

            // Reload messages
            if (plugin.getMessageManager() != null) {
                plugin.getMessageManager().reload();
            }

            // Reload debug mode in SessionManager
            if (plugin.getSessionManager() != null) {
                plugin.getSessionManager().reloadDebugMode();
            }

            // Reload milestones
            if (plugin.getMilestoneManager() != null) {
                plugin.getMilestoneManager().reload();
            }

            sender.sendMessage(plugin.getMessageManager().getMessage("commands.reload.success"));

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[DEBUG] Configuration reloaded by " + sender.getName());
            }
        } catch (Exception e) {
            sender.sendMessage(plugin.getMessageManager().getMessage("commands.reload.error"));
            plugin.getLogger().severe("Error reloading configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles the info/version subcommand.
     * Displays plugin information.
     *
     * @param sender Command sender
     */
    private void handleInfo(CommandSender sender) {
        String placeholderStatus = plugin.isPlaceholderAPIEnabled() ?
                plugin.getMessageManager().getMessage("commands.info.placeholderapi-enabled") :
                plugin.getMessageManager().getMessage("commands.info.placeholderapi-disabled");

        sender.sendMessage(plugin.getMessageManager().getMessage("commands.info.header"));

        List<String> infoLines = plugin.getMessageManager().getMessageList("commands.info.lines",
                "{version}", plugin.getDescription().getVersion(),
                "{placeholderapi}", placeholderStatus);

        for (String line : infoLines) {
            sender.sendMessage(line);
        }

        sender.sendMessage(plugin.getMessageManager().getMessage("commands.info.footer"));
    }

    /**
     * Handles the debug subcommand.
     * Toggles debug mode on/off.
     *
     * @param sender Command sender
     */
    private void handleDebug(CommandSender sender) {
        if (!sender.hasPermission("simplesession.admin")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("commands.no-permission"));
            return;
        }

        boolean currentDebug = plugin.getConfig().getBoolean("debug", false);
        boolean newDebug = !currentDebug;

        plugin.getConfig().set("debug", newDebug);
        plugin.saveConfig();

        // Reload debug mode in SessionManager
        if (plugin.getSessionManager() != null) {
            plugin.getSessionManager().reloadDebugMode();
        }

        String message = newDebug ?
                plugin.getMessageManager().getMessage("commands.debug.enabled") :
                plugin.getMessageManager().getMessage("commands.debug.disabled");

        sender.sendMessage(message);

        if (newDebug) {
            plugin.getLogger().info("[DEBUG] Debug mode enabled by " + sender.getName());
        } else {
            plugin.getLogger().info("Debug mode disabled by " + sender.getName());
        }
    }

    /**
     * Handles the top subcommand.
     * Displays top players by current session time (configurable size).
     *
     * @param sender Command sender
     */
    private void handleTop(CommandSender sender) {
        // Get sorted players from SessionManager (uses cache)
        java.util.List<org.bukkit.entity.Player> sortedPlayers =
            plugin.getSessionManager().getSortedPlayers();

        if (sortedPlayers.isEmpty()) {
            sender.sendMessage(plugin.getMessageManager().getMessage("commands.top.no-players"));
            return;
        }

        // Get leaderboard size from config
        int topSize = plugin.getConfig().getInt("leaderboard.top-size", 10);

        // Validate top-size is positive
        if (topSize <= 0) {
            plugin.getLogger().warning("Invalid leaderboard.top-size (" + topSize + "). Using default 10.");
            topSize = 10;
        }

        // Get format settings from config
        String header = plugin.getConfig().getString("leaderboard.format.header", "&7╔════════════════════════════════╗");
        String separator = plugin.getConfig().getString("leaderboard.format.separator", "&7╠════════════════════════════════╣");
        String lineFormat = plugin.getConfig().getString("leaderboard.format.line", "&7║ {medal} {rank}. {player} &7- {color}{time}");
        String footer = plugin.getConfig().getString("leaderboard.format.footer", "&7╚════════════════════════════════╝");

        // Get medals from config
        String medalFirst = plugin.getConfig().getString("leaderboard.format.medals.first", "🥇");
        String medalSecond = plugin.getConfig().getString("leaderboard.format.medals.second", "🥈");
        String medalThird = plugin.getConfig().getString("leaderboard.format.medals.third", "🥉");
        String medalOther = plugin.getConfig().getString("leaderboard.format.medals.other", "  ");

        // Get colors from config
        String colorFirst = plugin.getConfig().getString("leaderboard.format.colors.first", "&6");
        String colorSecond = plugin.getConfig().getString("leaderboard.format.colors.second", "&7");
        String colorThird = plugin.getConfig().getString("leaderboard.format.colors.third", "&c");
        String colorOther = plugin.getConfig().getString("leaderboard.format.colors.other", "&f");

        // Get title from config
        String title = plugin.getConfig().getString("leaderboard.title", "&6&l🏆 TOP {size} - Bieżące Sesje");
        title = ChatColor.translateAlternateColorCodes('&', title)
                .replace("{size}", String.valueOf(topSize));

        // Display header
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', header));
        sender.sendMessage(title);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', separator));

        // Display players
        int limit = Math.min(topSize, sortedPlayers.size());
        for (int i = 0; i < limit; i++) {
            org.bukkit.entity.Player player = sortedPlayers.get(i);
            long sessionSeconds = plugin.getSessionManager().getSessionSeconds(player.getUniqueId());
            String formattedTime = formatSessionTime(sessionSeconds);

            // Determine medal and color based on rank
            String medal;
            String color;
            if (i == 0) {
                medal = medalFirst;
                color = colorFirst;
            } else if (i == 1) {
                medal = medalSecond;
                color = colorSecond;
            } else if (i == 2) {
                medal = medalThird;
                color = colorThird;
            } else {
                medal = medalOther;
                color = colorOther;
            }

            // Format and send line
            String line = lineFormat
                    .replace("{medal}", medal)
                    .replace("{rank}", String.valueOf(i + 1))
                    .replace("{player}", player.getName())
                    .replace("{time}", formattedTime)
                    .replace("{color}", color);

            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }

        // Display footer
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', footer));
    }

    /**
     * Formats session time in seconds to readable format.
     *
     * @param seconds Session time in seconds
     * @return Formatted time string
     */
    private String formatSessionTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }

    /**
     * Sends help message to the sender.
     *
     * @param sender Command sender
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("commands.help.header"));

        List<String> helpLines = plugin.getMessageManager().getMessageList("commands.help.list");
        for (String line : helpLines) {
            sender.sendMessage(line);
        }

        sender.sendMessage(plugin.getMessageManager().getMessage("commands.help.footer"));
    }

    /**
     * Handles tab completion for the command.
     *
     * @param sender Source of the command
     * @param command Command which was executed
     * @param alias Alias of the command used
     * @param args The arguments passed to the command
     * @return List of possible completions
     */
    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("help", "info", "version", "top"));

            if (sender.hasPermission("simplesession.admin")) {
                subCommands.add("reload");
                subCommands.add("debug");
            }

            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }
        }

        return completions;
    }
}
