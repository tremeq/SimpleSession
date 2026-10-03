package pl.tremeq.simplesession;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import pl.tremeq.simplesession.command.SimpleSessionCommand;
import pl.tremeq.simplesession.format.FormatManager;
import pl.tremeq.simplesession.manager.MessageManager;
import pl.tremeq.simplesession.milestone.MilestoneManager;
import pl.tremeq.simplesession.placeholder.SimpleSessionExpansion;
import pl.tremeq.simplesession.session.SessionManager;
import pl.tremeq.simplesession.stats.StatsManager;
import pl.tremeq.simplesession.util.ConfigUpdater;

import java.util.Set;

/**
 * SimpleSession - Modern session time tracking plugin for Minecraft
 *
 * Tracks the current session of every player, keeps persistent statistics (SQLite),
 * protects sessions against short relogs and provides PlaceholderAPI placeholders.
 *
 * @author TremeQ
 * @version 2.0.0
 */
public class SimpleSession extends JavaPlugin {

    private MessageManager messageManager;
    private FormatManager formatManager;
    private StatsManager statsManager;
    private SessionManager sessionManager;
    private MilestoneManager milestoneManager;
    private SimpleSessionExpansion expansion;
    private boolean placeholderAPIEnabled = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Add options introduced in newer versions to files created by an older version (with backup)
        if (ConfigUpdater.update(this, "config.yml", Set.of("milestones.list"), Set.of())) {
            reloadConfig();
        }
        ConfigUpdater.update(this, "messages.yml", Set.of(), Set.of("commands.help.list", "commands.info.lines"));

        messageManager = new MessageManager(this);
        debug("MessageManager initialized");

        formatManager = new FormatManager(getLogger());
        formatManager.load(getConfig());
        debug("Loaded time formats: " + formatManager.names());

        statsManager = new StatsManager(this);
        debug("StatsManager initialized (enabled: " + statsManager.isEnabled() + ")");

        sessionManager = new SessionManager(this);
        sessionManager.initialize();
        debug("SessionManager initialized");

        milestoneManager = new MilestoneManager(this);
        debug("MilestoneManager initialized");

        PluginCommand command = getCommand("simplesession");
        if (command != null) {
            SimpleSessionCommand executor = new SimpleSessionCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
            debug("Commands registered: /simplesession, /ss, /session");
        } else {
            getLogger().severe(messageManager.getMessage("plugin.command-registration-failed"));
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            expansion = new SimpleSessionExpansion(this);
            expansion.register();
            placeholderAPIEnabled = true;
            getLogger().info(messageManager.getMessage("plugin.placeholderapi-registered"));
        } else {
            getLogger().warning(messageManager.getMessage("plugin.placeholderapi-not-found"));
        }

        getLogger().info(messageManager.getMessage("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        if (milestoneManager != null) {
            milestoneManager.shutdown();
        }
        if (statsManager != null) {
            statsManager.shutdown();
        }
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        if (messageManager != null) {
            getLogger().info(messageManager.getMessage("plugin.disabled"));
        }
    }

    /**
     * Reloads config.yml, messages.yml and every module.
     */
    public void reloadPlugin() {
        reloadConfig();
        messageManager.reload();
        formatManager.load(getConfig());
        sessionManager.loadSettings();
        statsManager.reload();
        milestoneManager.reload();
        if (expansion != null) {
            expansion.reload();
        }
    }

    /**
     * Logs a message when debug mode is enabled.
     *
     * @param message Message
     */
    public void debug(String message) {
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public FormatManager getFormatManager() {
        return formatManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public MilestoneManager getMilestoneManager() {
        return milestoneManager;
    }

    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }
}
