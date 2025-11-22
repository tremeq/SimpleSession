package pl.tremeq.simplesession.milestone;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import pl.tremeq.simplesession.SimpleSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages session milestones for players.
 *
 * This manager checks player session times periodically and triggers
 * milestones when players reach specific durations.
 *
 * @author TremeQ
 */
public class MilestoneManager implements Listener {

    private final SimpleSession plugin;
    private final Map<UUID, Set<String>> playerMilestones; // playerId -> set of achieved milestone IDs
    private final List<Milestone> milestones;
    private BukkitTask checkTask;
    private boolean enabled;

    /**
     * Creates a new MilestoneManager.
     *
     * @param plugin The main plugin instance
     */
    public MilestoneManager(SimpleSession plugin) {
        this.plugin = plugin;
        this.playerMilestones = new ConcurrentHashMap<>();
        this.milestones = new ArrayList<>();

        // Load milestones from config
        loadMilestones();

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Start checking task if enabled
        if (enabled && !milestones.isEmpty()) {
            startCheckTask();
        }
    }

    /**
     * Loads milestones from the plugin configuration.
     */
    private void loadMilestones() {
        milestones.clear();

        // Check if milestones are enabled
        enabled = plugin.getConfig().getBoolean("milestones.enabled", false);

        if (!enabled) {
            plugin.getLogger().info("Milestones are disabled in config");
            return;
        }

        ConfigurationSection milestonesSection = plugin.getConfig().getConfigurationSection("milestones.list");

        if (milestonesSection == null) {
            plugin.getLogger().warning("No milestones configured in config.yml!");
            return;
        }

        // Load each milestone
        for (String key : milestonesSection.getKeys(false)) {
            ConfigurationSection milestoneSection = milestonesSection.getConfigurationSection(key);

            if (milestoneSection == null) continue;

            try {
                int time = milestoneSection.getInt("time");

                // Validate time is positive
                if (time <= 0) {
                    plugin.getLogger().warning("Milestone '" + key + "' has invalid time (" + time + "s). Skipping.");
                    continue;
                }

                String message = milestoneSection.getString("message", "");
                List<String> commands = milestoneSection.getStringList("commands");

                Milestone milestone = new Milestone(key, time, message, commands);
                milestones.add(milestone);

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("[DEBUG] Loaded milestone: " + key + " at " + time + "s");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load milestone '" + key + "': " + e.getMessage());
            }
        }

        // Sort milestones by time (ascending)
        milestones.sort(Comparator.comparingInt(Milestone::getTimeSeconds));

        plugin.getLogger().info("Loaded " + milestones.size() + " milestones");
    }

    /**
     * Starts the periodic task that checks for milestone achievements.
     */
    private void startCheckTask() {
        // Check every minute (1200 ticks = 60 seconds)
        int intervalSeconds = plugin.getConfig().getInt("milestones.check-interval", 60);

        // Validate interval is positive (minimum 1 second)
        if (intervalSeconds <= 0) {
            plugin.getLogger().warning("Invalid check-interval (" + intervalSeconds + "s). Using default 60s.");
            intervalSeconds = 60;
        }

        int intervalTicks = intervalSeconds * 20;

        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkMilestones();
        }, intervalTicks, intervalTicks);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] Milestone check task started (interval: " + (intervalTicks / 20) + "s)");
        }
    }

    /**
     * Checks all online players for milestone achievements.
     */
    private void checkMilestones() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayerMilestones(player);
        }
    }

    /**
     * Checks if a specific player has achieved any new milestones.
     *
     * @param player The player to check
     */
    private void checkPlayerMilestones(Player player) {
        UUID playerId = player.getUniqueId();

        // Get player's current session time in seconds
        long sessionSeconds = plugin.getSessionManager().getSessionSeconds(playerId);

        // Get set of already achieved milestones for this session
        Set<String> achieved = playerMilestones.computeIfAbsent(playerId, k -> new HashSet<>());

        // Check each milestone
        for (Milestone milestone : milestones) {
            // Has the player reached this milestone time?
            if (sessionSeconds >= milestone.getTimeSeconds()) {
                // Has the player already received this milestone?
                if (!achieved.contains(milestone.getId())) {
                    // Grant the milestone!
                    grantMilestone(player, milestone);
                    achieved.add(milestone.getId());

                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("[DEBUG] Player " + player.getName() +
                                " achieved milestone: " + milestone.getId());
                    }
                }
            }
        }
    }

    /**
     * Grants a milestone to a player.
     *
     * @param player The player receiving the milestone
     * @param milestone The milestone to grant
     */
    private void grantMilestone(Player player, Milestone milestone) {
        // Execute the milestone (send message, run commands)
        milestone.execute(player);
    }

    /**
     * Handles player join event.
     * Initializes milestone tracking for the player.
     *
     * @param event PlayerJoinEvent
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        // Clear any previous milestone data (new session)
        playerMilestones.put(playerId, new HashSet<>());
    }

    /**
     * Handles player quit event.
     * Cleans up milestone tracking data.
     *
     * @param event PlayerQuitEvent
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        // Remove milestone data to free memory
        playerMilestones.remove(playerId);
    }

    /**
     * Reloads milestones from config.
     * Should be called when config is reloaded.
     */
    public void reload() {
        // Cancel existing task
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }

        // Reload milestones
        loadMilestones();

        // Restart task if enabled
        if (enabled && !milestones.isEmpty()) {
            startCheckTask();
        }

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] MilestoneManager reloaded");
        }
    }

    /**
     * Stops the milestone checking task.
     * Should be called when plugin is disabled.
     */
    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        playerMilestones.clear();
    }

    /**
     * Gets the number of loaded milestones.
     *
     * @return Number of milestones
     */
    public int getMilestoneCount() {
        return milestones.size();
    }

    /**
     * Checks if milestones are enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
}
