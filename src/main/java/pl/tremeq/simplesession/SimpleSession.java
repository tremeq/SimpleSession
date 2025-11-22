package pl.tremeq.simplesession;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import pl.tremeq.simplesession.command.SimpleSessionCommand;
import pl.tremeq.simplesession.manager.SessionManager;
import pl.tremeq.simplesession.milestone.MilestoneManager;
import pl.tremeq.simplesession.placeholder.SimpleSessionExpansion;

/**
 * SimpleSession - Modern session time tracking plugin for Minecraft
 *
 * This plugin tracks player session time and provides PlaceholderAPI integration
 * for displaying session duration in various formats.
 *
 * @author TremeQ
 * @version 1.0.0
 */
public class SimpleSession extends JavaPlugin {

    private SessionManager sessionManager;
    private MilestoneManager milestoneManager;
    private boolean placeholderAPIEnabled = false;

    /**
     * Called when the plugin is enabled.
     * Initializes managers, registers listeners and PlaceholderAPI expansion.
     */
    @Override
    public void onEnable() {
        // Save default configuration if it doesn't exist
        saveDefaultConfig();

        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("[DEBUG] Debug mode is enabled");
            getLogger().info("[DEBUG] Loading configuration...");
        }

        // Initialize session manager
        sessionManager = new SessionManager(this);

        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("[DEBUG] SessionManager initialized");
        }

        // Initialize milestone manager
        milestoneManager = new MilestoneManager(this);

        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("[DEBUG] MilestoneManager initialized");
        }

        // Register command
        SimpleSessionCommand commandExecutor = new SimpleSessionCommand(this);
        if (getCommand("simplesession") != null) {
            getCommand("simplesession").setExecutor(commandExecutor);
            getCommand("simplesession").setTabCompleter(commandExecutor);

            if (getConfig().getBoolean("debug", false)) {
                getLogger().info("[DEBUG] Commands registered: /simplesession, /ss, /session");
            }
        } else {
            getLogger().severe("Failed to register command 'simplesession'! Check plugin.yml");
        }

        // Register PlaceholderAPI expansion if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SimpleSessionExpansion(this).register();
            placeholderAPIEnabled = true;
            getLogger().info("PlaceholderAPI hook registered successfully!");

            if (getConfig().getBoolean("debug", false)) {
                getLogger().info("[DEBUG] PlaceholderAPI expansion registered");
            }
        } else {
            getLogger().warning("PlaceholderAPI not found! Placeholder functionality will be disabled.");
        }

        getLogger().info("SimpleSession has been enabled successfully!");
    }

    /**
     * Called when the plugin is disabled.
     * Cleans up resources and saves session data.
     */
    @Override
    public void onDisable() {
        // Shutdown milestone manager
        if (milestoneManager != null) {
            milestoneManager.shutdown();
        }

        // Clear all active sessions
        if (sessionManager != null) {
            sessionManager.clearAllSessions();
        }

        getLogger().info("SimpleSession has been disabled!");
    }

    /**
     * Gets the session manager instance.
     *
     * @return SessionManager instance
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Gets the milestone manager instance.
     *
     * @return MilestoneManager instance
     */
    public MilestoneManager getMilestoneManager() {
        return milestoneManager;
    }

    /**
     * Checks if PlaceholderAPI is enabled.
     *
     * @return true if PlaceholderAPI is enabled, false otherwise
     */
    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }
}
